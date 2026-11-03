package dev.oreandorder;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;

public record PlannerConfig(URI endpoint, String model, String apiKeyEnv, Duration timeout) {
    public static final String DEFAULT_MODEL = "nvidia/nemotron-3.5-super-vl-120b-a12b";

    public PlannerConfig {
        boolean local = "localhost".equalsIgnoreCase(endpoint.getHost())
                || "127.0.0.1".equals(endpoint.getHost()) || "[::1]".equals(endpoint.getHost());
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !("https".equals(endpoint.getScheme()) || (local && "http".equals(endpoint.getScheme()))))
            throw new IllegalArgumentException("Endpoint must use HTTPS (HTTP allowed only on loopback).");
        if (model == null || model.isBlank() || apiKeyEnv == null || apiKeyEnv.isBlank()
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toSeconds() > 120)
            throw new IllegalArgumentException("Invalid planner configuration.");
    }

    public static PlannerConfig load(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        } else {
            Files.createDirectories(path.getParent());
            p.setProperty("endpoint", "https://integrate.api.nvidia.com/v1/chat/completions");
            p.setProperty("model", DEFAULT_MODEL);
            p.setProperty("apiKeyEnv", "NVIDIA_API_KEY");
            p.setProperty("timeoutSeconds", "45");
            try (Writer writer = Files.newBufferedWriter(path)) {
                p.store(writer, "Ore & Order server configuration. Restart after changes. Never put a key here.");
            }
        }
        return new PlannerConfig(URI.create(p.getProperty("endpoint", "https://integrate.api.nvidia.com/v1/chat/completions")),
                p.getProperty("model", DEFAULT_MODEL), p.getProperty("apiKeyEnv", "NVIDIA_API_KEY"),
                Duration.ofSeconds(Long.parseLong(p.getProperty("timeoutSeconds", "45"))));
    }
}
