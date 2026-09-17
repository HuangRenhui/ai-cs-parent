package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.IndustryPromptPack;
import com.ai.cs.open.mapper.IndustryPromptPackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 行业提示词包服务：管理行业特定的人设、拒答、槽位等提示词。
 */
@Service
public class IndustryPromptPackService extends ServiceImpl<IndustryPromptPackMapper, IndustryPromptPack> {

    @Resource
    private IndustryPromptPackMapper promptPackMapper;

    /**
     * 查询全部行业提示词包，按优先级升序、ID 升序返回。
     */
    public List<IndustryPromptPack> listPromptPacks() {
        return promptPackMapper.selectList(new LambdaQueryWrapper<IndustryPromptPack>()
                .orderByAsc(IndustryPromptPack::getPriority)
                .orderByAsc(IndustryPromptPack::getId));
    }

    /**
     * 按行业包查询提示词。
     */
    public List<IndustryPromptPack> listPromptPacksByPack(String packCode) {
        return promptPackMapper.selectList(new LambdaQueryWrapper<IndustryPromptPack>()
                .eq(IndustryPromptPack::getPackCode, packCode)
                .eq(IndustryPromptPack::getEnabled, 1)
                .orderByAsc(IndustryPromptPack::getPriority)
                .orderByAsc(IndustryPromptPack::getId));
    }

    /**
     * 按行业包和类型查询提示词。
     */
    public List<IndustryPromptPack> listPromptPacksByPackAndType(String packCode, String promptType) {
        return promptPackMapper.selectList(new LambdaQueryWrapper<IndustryPromptPack>()
                .eq(IndustryPromptPack::getPackCode, packCode)
                .eq(IndustryPromptPack::getPromptType, promptType)
                .eq(IndustryPromptPack::getEnabled, 1)
                .orderByAsc(IndustryPromptPack::getPriority)
                .orderByAsc(IndustryPromptPack::getId));
    }

    /**
     * 按行业包、类型和场景查询提示词。
     */
    public IndustryPromptPack findPromptPack(String packCode, String promptType, String scene) {
        LambdaQueryWrapper<IndustryPromptPack> wrapper = new LambdaQueryWrapper<IndustryPromptPack>()
                .eq(IndustryPromptPack::getPackCode, packCode)
                .eq(IndustryPromptPack::getPromptType, promptType)
                .eq(IndustryPromptPack::getEnabled, 1)
                .orderByAsc(IndustryPromptPack::getPriority)
                .orderByAsc(IndustryPromptPack::getId)
                .last("limit 1");
        
        if (StringUtils.hasText(scene)) {
            wrapper.eq(IndustryPromptPack::getScene, scene);
        } else {
            wrapper.isNull(IndustryPromptPack::getScene);
        }
        
        return promptPackMapper.selectOne(wrapper);
    }

    /**
     * 新增或更新行业提示词包。
     */
    public void savePromptPack(IndustryPromptPack promptPack) {
        if (promptPack == null) {
            throw new BusinessException("提示词包不能为空");
        }
        if (!StringUtils.hasText(promptPack.getPackCode())) {
            throw new BusinessException("行业包编码不能为空");
        }
        if (!StringUtils.hasText(promptPack.getPromptType())) {
            throw new BusinessException("提示词类型不能为空");
        }
        if (!StringUtils.hasText(promptPack.getPromptContent())) {
            throw new BusinessException("提示词内容不能为空");
        }
        if (promptPack.getPriority() == null) {
            promptPack.setPriority(0);
        }
        if (promptPack.getEnabled() == null) {
            promptPack.setEnabled(1);
        }
        if (promptPack.getId() == null) {
            promptPackMapper.insert(promptPack);
        } else {
            promptPackMapper.updateById(promptPack);
        }
    }

    /**
     * 删除行业提示词包（逻辑删除）。
     */
    public void deletePromptPack(Long id) {
        promptPackMapper.deleteById(id);
    }

    /**
     * 启用/停用行业提示词包。
     */
    public void setPromptPackEnabled(Long id, Integer enabled) {
        IndustryPromptPack promptPack = promptPackMapper.selectById(id);
        if (promptPack == null) {
            throw new BusinessException("行业提示词包不存在");
        }
        promptPack.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        promptPackMapper.updateById(promptPack);
    }

    /**
     * 加载行业包的所有提示词，组合成完整的系统提示词。
     */
    public String loadPromptPack(String packCode) {
        List<IndustryPromptPack> packs = listPromptPacksByPack(packCode);
        StringBuilder fullPrompt = new StringBuilder();
        
        for (IndustryPromptPack pack : packs) {
            if (StringUtils.hasText(pack.getPromptContent())) {
                fullPrompt.append(pack.getPromptContent()).append("\n\n");
            }
        }
        
        return fullPrompt.toString();
    }

    /**
     * 批量导入行业提示词包。
     */
    @Transactional
    public void batchImportPromptPacks(String packCode, List<IndustryPromptPack> packs) {
        if (!StringUtils.hasText(packCode)) {
            throw new BusinessException("行业包编码不能为空");
        }
        // 先删除该行业包的旧提示词
        promptPackMapper.delete(new LambdaQueryWrapper<IndustryPromptPack>()
                .eq(IndustryPromptPack::getPackCode, packCode));
        // 批量插入新提示词
        for (IndustryPromptPack pack : packs) {
            pack.setPackCode(packCode);
            pack.setId(null);
            savePromptPack(pack);
        }
    }
}
