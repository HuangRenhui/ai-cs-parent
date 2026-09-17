package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * 音频波形服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>{@link #generateWaveformWithFFmpeg}：调用 ffmpeg 抽取采样并绘制波形图（依赖外部进程）；</li>
 *   <li>{@link #generateWaveformWithJava}：用 Java 音频 API 读取采样后绘制（无外部依赖的回退实现）；</li>
 *   <li>{@link #generateStereoWaveform}：左右声道分别绘制；</li>
 *   <li>波形绘制统一走私有方法（按采样值映射高度、画轴与背景、输出图片文件）。</li>
 * </ul>
 * </p>
 *
 * <p>当前不生成波形：三个入口一律抛 {@link IOException}（占位），上层按异常分支降级。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioWaveformService {

    private final AudioProperties audioProperties;

    /**
     * 用 ffmpeg 生成波形图（占位：抛 IOException）
     *
     * @param audioFile 音频文件
     * @param fileId    文件 ID
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String generateWaveformWithFFmpeg(File audioFile, String fileId) throws IOException {
        log.warn("[占位] ffmpeg 波形生成未实现 fileId={}", fileId);
        throw new IOException("波形生成为占位实现");
    }

    /**
     * 用 Java 音频 API 生成波形图（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String generateWaveformWithJava(File audioFile, String fileId) throws IOException {
        log.warn("[占位] Java 波形生成未实现 fileId={}", fileId);
        throw new IOException("波形生成为占位实现");
    }

    /**
     * 生成左右声道波形图（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String generateStereoWaveform(File audioFile, String fileId) throws IOException {
        log.warn("[占位] 立体声波形生成未实现 fileId={}", fileId);
        throw new IOException("波形生成为占位实现");
    }
}
