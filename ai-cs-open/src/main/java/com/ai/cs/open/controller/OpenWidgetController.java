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

@RestController
@RequestMapping("/open/widget")
public class OpenWidgetController {

    @Resource
    private OpenPlatformService openPlatformService;

    @PostMapping("/init")
    public Result<WidgetInitVO> init(@RequestBody WidgetInitDTO dto) {
        return Result.success(openPlatformService.initWidget(dto));
    }
}
