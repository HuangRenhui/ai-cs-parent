package com.ai.cs.base.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 对话附件静态资源的安全响应头。
 *
 * <p>附件目录 {@code /files/chat/**} 是同源托管的：即使上传侧已限制扩展名，
 * 历史文件或漏网文件仍可能被浏览器当 HTML 渲染。这里的兜底策略：
 * <ul>
 *   <li>所有响应加 {@code X-Content-Type-Options: nosniff}，禁止 MIME 嗅探；</li>
 *   <li>图片/音视频保留内联，便于聊天气泡展示；</li>
 *   <li>其余类型强制 {@code Content-Disposition: attachment}，不在同源上下文渲染。</li>
 * </ul>
 *
 * @author ai-cs
 */
@Component
@Order(-80)
public class ChatFileSecurityFilter extends OncePerRequestFilter {

    private static final String CHAT_PREFIX = "/files/chat/";

    /** 允许浏览器内联渲染的类型：图片/音频/视频 */
    private static final Set<String> INLINE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic",
            ".mp3", ".wav", ".amr", ".m4a", ".aac", ".ogg", ".flac",
            ".mp4", ".mov", ".webm");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith(CHAT_PREFIX)) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self'; media-src 'self'");
            String ext = extensionOf(path);
            if (!INLINE_EXTENSIONS.contains(ext)) {
                response.setHeader("Content-Disposition", "attachment");
                response.setContentType("application/octet-stream");
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extensionOf(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }
}
