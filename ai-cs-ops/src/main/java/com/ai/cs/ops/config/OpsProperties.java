package com.ai.cs.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ops")
public class OpsProperties {

    private List<String> logDirs = new ArrayList<>(List.of("logs"));
    private int maxLinesPerFile = 4000;
    private int connectTimeoutMs = 800;
    private List<ServiceEndpoint> services = new ArrayList<>();

    @Data
    public static class ServiceEndpoint {
        private String name;
        private String url;
    }
}
