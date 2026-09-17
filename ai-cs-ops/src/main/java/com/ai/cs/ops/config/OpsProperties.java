package com.ai.cs.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 运营中心配置（前缀 ops）：日志扫描目录、单文件读取上限、探测超时与被探测服务清单。
 */
@Data
@ConfigurationProperties(prefix = "ops")
public class OpsProperties {

    /** 额外扫描的日志目录（相对工作目录或绝对路径），默认 logs */
    private List<String> logDirs = new ArrayList<>(List.of("logs"));
    /** 单文件最多读取的尾部行数，防止大文件拖垮内存 */
    private int maxLinesPerFile = 4000;
    /** 健康探测的连接/读取超时（毫秒） */
    private int connectTimeoutMs = 800;
    /** 被探测的服务清单 */
    private List<ServiceEndpoint> services = new ArrayList<>();

    /**
     * 被探测服务端点：名称 + 健康检查 URL。
     */
    @Data
    public static class ServiceEndpoint {
        /** 服务名 */
        private String name;
        /** 探测地址 */
        private String url;
    }
}
