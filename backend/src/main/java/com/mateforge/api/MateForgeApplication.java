package com.mateforge.api;

import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MateForgeApplication {
    public static void main(String[] args) {
        normalizeRenderDatabaseUrl();
        SpringApplication.run(MateForgeApplication.class, args);
    }

    private static void normalizeRenderDatabaseUrl() {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return;
        }
        URI uri = URI.create(databaseUrl);
        String[] userInfo = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
            + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
            + uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbcUrl += "?" + uri.getQuery();
        }
        System.setProperty("DATABASE_URL", jdbcUrl);
        if (userInfo.length > 0 && !userInfo[0].isBlank()) {
            System.setProperty("DATABASE_USERNAME", userInfo[0]);
        }
        if (userInfo.length > 1 && !userInfo[1].isBlank()) {
            System.setProperty("DATABASE_PASSWORD", userInfo[1]);
        }
    }
}
