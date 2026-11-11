package com.ledgerline;

import java.net.URI;

public final class DatabaseUrlNormalizer {
    private DatabaseUrlNormalizer() {}

    public static void normalize() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            return;
        }

        if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
            try {
                String clean = dbUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = URI.create(clean);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                if (path == null || path.isBlank() || path.equals("/")) {
                    path = "/ledgerline";
                }
                String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + query;

                System.setProperty("spring.datasource.url", jdbcUrl);
                System.setProperty("spring.flyway.url", jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        System.setProperty("spring.datasource.username", parts[0]);
                        System.setProperty("spring.flyway.user", parts[0]);
                    }
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        System.setProperty("spring.datasource.password", parts[1]);
                        System.setProperty("spring.flyway.password", parts[1]);
                    }
                }
            } catch (Exception ignored) {
                if (!dbUrl.startsWith("jdbc:")) {
                    String fallback = "jdbc:" + dbUrl;
                    System.setProperty("spring.datasource.url", fallback);
                    System.setProperty("spring.flyway.url", fallback);
                }
            }
        }
    }
}

