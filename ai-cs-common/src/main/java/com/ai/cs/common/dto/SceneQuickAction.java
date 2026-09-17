package com.ai.cs.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 场景快捷动作：Widget 首屏渲染为可点按钮，点击后以 send 文本走正常对话链路。
 *
 * @author ai-cs
 */
@Data
public class SceneQuickAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 按钮文案，例如「查看订单情况」 */
    private String label;

    /** 点击后发送的文本，例如「帮我查一下这个订单的情况」 */
    private String send;
}
