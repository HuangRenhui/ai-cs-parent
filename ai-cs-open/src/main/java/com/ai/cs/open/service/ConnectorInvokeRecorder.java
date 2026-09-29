package com.ai.cs.open.service;

import com.ai.cs.open.entity.OpenTool;
import com.ai.cs.open.entity.OpenToolInvoke;
import com.ai.cs.open.mapper.OpenToolInvokeMapper;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 工具调用记录落库组件。
 *
 * <p><b>为什么必须独立成 Bean 且用 {@code REQUIRES_NEW}</b>：</p>
 * <p>调用主链路 {@code OpenPlatformService#invoke} 是带事务的。如果把「插入幂等占位行」
 * 写在同一个事务里，一旦撞唯一索引（并发重复提交的正常情况），数据库会把这个事务标记为
 * rollback-only。此时即便在代码里 catch 住 {@code DataIntegrityViolationException} 并想
 * 返回「请勿重复提交」，外层提交时仍会抛 {@code UnexpectedRollbackException}——
 * 用户看到的是 500 而非业务提示。</p>
 *
 * <p>独立事务后：占位插入失败只回滚它自己那一段，主链路事务不受污染，可以继续正常返回。
 * 同理，失败留痕也必须走独立事务，否则「抛异常 → 主事务回滚」会把失败记录一起抹掉，
 * 导致线上排查时看不到任何痕迹。</p>
 *
 * <p>另外，Spring 的 {@code @Transactional} 基于代理，同类内部方法调用不会走代理，
 * 因此这个组件必须是<b>独立 Bean</b>而非 {@code OpenPlatformService} 的私有方法。</p>
 */
@Slf4j
@Component
public class ConnectorInvokeRecorder {

    @Resource
    private OpenToolInvokeMapper openToolInvokeMapper;

    /**
     * 查找幂等键下已成功的调用记录（success=1）。
     *
     * <p>只读操作，不新开事务，跟随调用方事务即可。</p>
     *
     * @param idempotencyKey 幂等键
     * @return 已成功的记录；不存在返回 null
     */
    public OpenToolInvoke findSucceeded(String idempotencyKey) {
        return openToolInvokeMapper.selectOne(new LambdaQueryWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getIdempotencyKey, idempotencyKey)
                .eq(OpenToolInvoke::getSuccess, 1)
                .last("limit 1"));
    }

    /**
     * 插入幂等占位行抢占执行权（独立事务）。
     *
     * <p><b>为什么靠唯一索引而不是「先查后插」</b>：并发下两个请求都会查到空，
     * 然后都去插，只有唯一索引能真正挡住。因此这里不做预查询，直接插、靠异常兜底。</p>
     *
     * <p>操作依赖 {@code cs_open_tool_invoke.idempotency_key} 上的唯一索引。
     * 该列允许 NULL 且 MySQL 下多个 NULL 不冲突，正好满足「未传幂等键不参与去重」。</p>
     *
     * @return 插入成功的占位记录；撞唯一索引（已被并发占用）返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OpenToolInvoke tryInsertPlaceholder(OpenTool tool, ToolInvokeDTO dto, String idempotencyKey) {
        OpenToolInvoke invoke = new OpenToolInvoke();
        invoke.setToolName(tool.getName());
        invoke.setSessionId(dto.getSessionId());
        invoke.setIdempotencyKey(idempotencyKey);
        invoke.setRequestJson(JSON.toJSONString(dto));
        // 0 表示「处理中」：占位行存在的意义就是占住幂等键，尚未有结果
        invoke.setSuccess(0);
        try {
            openToolInvokeMapper.insert(invoke);
            return invoke;
        } catch (DataIntegrityViolationException e) {
            // 唯一索引冲突 = 已有并发请求在同一幂等键上执行，属正常业务分支而非故障
            log.info("幂等键已被占用，判定为重复提交 tool={} key={}", tool.getName(), idempotencyKey);
            return null;
        }
    }

    /**
     * 回写调用结果（独立事务）。
     *
     * <p>有占位行则原地更新（保持一条记录对应一次逻辑调用），
     * 无占位行（未传幂等键）则追加审计记录。</p>
     *
     * <p>失败时 {@code failReason} 非空，写入 {@code responseJson}——失败记录同样要留痕，
     * 排查线上问题时它往往比成功记录更有价值。</p>
     *
     * <p><b>注意</b>：本方法自身不抛异常，落库失败只记日志。原因是它可能被「异常路径」调用，
     * 若这里再抛异常会覆盖掉真正的业务异常，让排查方向跑偏。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writeResult(OpenToolInvoke placeholder, OpenTool tool, ToolInvokeDTO dto,
                            String output, String failReason, int success) {
        String payload = StringUtils.hasText(failReason)
                ? failReason
                : (output == null ? "" : output);
        try {
            if (placeholder != null && placeholder.getId() != null) {
                placeholder.setResponseJson(payload);
                placeholder.setSuccess(success);
                openToolInvokeMapper.updateById(placeholder);
                return;
            }
            OpenToolInvoke record = new OpenToolInvoke();
            record.setToolName(tool.getName());
            record.setSessionId(dto.getSessionId());
            record.setRequestJson(JSON.toJSONString(dto));
            record.setResponseJson(payload);
            record.setSuccess(success);
            openToolInvokeMapper.insert(record);
        } catch (Exception e) {
            // 留痕失败不阻断主流程：结果已产生，不应因日志写不进去而让用户看到报错
            log.error("工具调用记录落库失败 tool={} sessionId={}", tool.getName(), dto.getSessionId(), e);
        }
    }
}
