package com.ai.cs.open.controller;

import com.ai.cs.common.dto.WidgetInitDTO;
import com.ai.cs.common.dto.WidgetInitVO;
import com.ai.cs.common.result.Result;
import com.ai.cs.open.service.OpenPlatformService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 访客聊窗 Widget 控制器：站点/小程序嵌入式聊窗的初始化入口。
 */
@RestController
@RequestMapping("/open/widget")
public class OpenWidgetController {

    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * Widget 初始化：建立或更新「租户+渠道+访客标识」映射，解析场景配置下发开场白与快捷动作，
     * 并签发访客访问令牌，供前端拿到后直接建联聊天。
     */
    @PostMapping("/init")
    public Result<WidgetInitVO> init(@RequestBody WidgetInitDTO dto) {
        return Result.success(openPlatformService.initWidget(dto));
    }
}
