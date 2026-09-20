package com.ai.cs.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 对话附件 DTO：描述随消息一起发送的图片 / 文档 / 音频等附件。
 *
 * <p>当前阶段附件仅做「上传 + 展示 + 落库」，AI 暂不解析附件内容；
 * 多模态理解能力（图片理解、文档解析入上下文）在模型层打通后接入，
 * 届时把 {@link #extractedText} 填充为解析结果即可直接投喂大模型，无需改动结构。</p>
 *
 * @author huangrenhui
 * @date 2026/9/21
 */
@Data
public class AttachmentDTO implements Serializable {

    /** 附件唯一标识（上传接口返回，用于去重与追溯） */
    private String fileId;

    /** 附件访问地址（相对路径或完整 URL） */
    private String url;

    /** 原始文件名，展示与下载时使用 */
    private String fileName;

    /**
     * 附件大类：image / document / audio / video / archive / other
     * <p>由上传接口根据扩展名与魔数判定后回填，前端按此选择渲染方式。</p>
     */
    private String category;

    /** MIME 类型，如 image/png、application/pdf */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 图片宽高（仅图片类附件有值，用于前端占位避免布局抖动） */
    private Integer width;

    /** 图片高度（仅图片类附件有值） */
    private Integer height;

    /** 缩略图地址（仅图片类附件有值，列表展示用） */
    private String thumbnailUrl;

    /**
     * 附件解析出的纯文本内容（多模态预留字段）。
     * <p>当前阶段为 null；后续文档解析、图片 OCR、语音转写实现后回填，
     * 由 {@code AiAgentService} 拼装进大模型上下文。</p>
     */
    private String extractedText;

    /**
     * 解析状态：0-未解析 / 1-解析中 / 2-解析成功 / 3-解析失败
     * <p>当前阶段固定为 0（未解析），前端可据此提示「AI 暂不支持读取该附件」。</p>
     */
    private Integer parseStatus;

    /** 解析失败原因（parseStatus=3 时有值） */
    private String parseError;
}
