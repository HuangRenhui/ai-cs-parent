package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.OpenPack;
import com.ai.cs.open.service.OpenPlatformService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 行业包管理控制器：行业包（电商/金融等）的查询、增删改、启停与热切换。
 *
 * <p>查询、启停、热切换已落地；<b>新增/编辑/删除当前为占位实现</b>，见各方法上的 TODO。</p>
 *
 * <p>注：本模块未引入 Swagger 依赖，接口说明沿用 Javadoc，与模块内其它 Controller 保持一致。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@RestController
@RequestMapping("/open/pack")
public class OpenPackController {

    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * 查询全部行业包（按排序号升序）。
     */
    @GetMapping("/list")
    public Result<List<OpenPack>> list() {
        return Result.success(openPlatformService.listPacks());
    }

    /**
     * 启用/停用指定行业包；停用后包下工具立即不可调用。
     *
     * @param code    行业包编码
     * @param enabled 1 启用 0 停用
     */
    @PutMapping("/{code}/enabled")
    public Result<Void> enabled(@PathVariable String code, @RequestParam Integer enabled) {
        openPlatformService.setPackEnabled(code, enabled);
        return Result.success();
    }

    /**
     * 只启用这一个行业包，其余关闭（热切换）。
     *
     * @param code 行业包编码
     */
    @PutMapping("/{code}/activate")
    public Result<Void> activate(@PathVariable String code) {
        openPlatformService.activatePackExclusive(code);
        return Result.success();
    }

    /**
     * 新增行业包（占位实现）。
     *
     * <p>TODO 后续实现：code 唯一校验（英文小写，建议只允许 [a-z0-9_-]）后落库 cs_open_pack；
     * 新包的 enabled 默认取 0，避免建完就影响线上工具可见性。</p>
     *
     * @param pack 行业包定义（code / name / remark / enabled / sortNum）
     * @return 处理结果说明
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody OpenPack pack) {
        if (pack.getCode() == null || pack.getCode().isBlank()) {
            return Result.fail("请填写行业包编码");
        }
        if (pack.getName() == null || pack.getName().isBlank()) {
            return Result.fail("请填写行业包名称");
        }
        log.info("[占位] 收到新增行业包请求（未持久化）: code={}, name={}", pack.getCode(), pack.getName());
        return Result.success("行业包已接收（后端暂未实现持久化）");
    }

    /**
     * 编辑行业包（占位实现）。
     *
     * <p>TODO 后续实现：code 是业务主键，建好后不允许修改；只更新 name / remark / sortNum。</p>
     *
     * @param pack 行业包定义，按 code 定位
     * @return 处理结果说明
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody OpenPack pack) {
        if (pack.getCode() == null || pack.getCode().isBlank()) {
            return Result.fail("缺少行业包编码");
        }
        log.info("[占位] 收到编辑行业包请求（未持久化）: code={}, name={}", pack.getCode(), pack.getName());
        return Result.success("行业包已接收（后端暂未实现持久化）");
    }

    /**
     * 删除行业包（占位实现）。
     *
     * <p>TODO 后续实现：先校验没有连接器/工具还挂在该包下，否则删除会让它们失去归属；
     * 建议返回明确提示，由前端二次确认后再强制删除。</p>
     *
     * @param code 行业包编码
     * @return 处理结果说明
     */
    @DeleteMapping("/delete/{code}")
    public Result<String> delete(@PathVariable String code) {
        log.info("[占位] 收到删除行业包请求（未生效）: code={}", code);
        return Result.success("删除请求已接收（后端暂未实现）");
    }
}
