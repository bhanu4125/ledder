package com.ledgerline;

import java.net.URI;

public final class DatabaseUrlNormalizer {
    private DatabaseUrlNormalizer() {}

    public static void normalize() {
        normalizeVar("DATABASE_URL");
        normalizeVar("FLYWAY_URL");
    }

    private static void normalizeVar(String varName) {
        String dbUrl = System.getenv(varName);
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty(varName);
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

                System.setProperty(varName, jdbcUrl);
                if ("DATABASE_URL".equals(varName)) {
                    System.setProperty("spring.datasource.url", jdbcUrl);
                    if (System.getenv("FLYWAY_URL") == null && System.getProperty("FLYWAY_URL") == null) {
                        System.setProperty("FLYWAY_URL", jdbcUrl);
                        System.setProperty("spring.flyway.url", jdbcUrl);
                    }
                } else if ("FLYWAY_URL".equals(varName)) {
                    System.setProperty("spring.flyway.url", jdbcUrl);
                }

                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        if ("DATABASE_URL".equals(varName)) {
                            System.setProperty("DATABASE_USERNAME", parts[0]);
                            System.setProperty("spring.datasource.username", parts[0]);
                            if (System.getenv("FLYWAY_USERNAME") == null && System.getProperty("FLYWAY_USERNAME") == null) {
                                System.setProperty("FLYWAY_USERNAME", parts[0]);
                                System.setProperty("spring.flyway.user", parts[0]);
                            }
                        } else if ("FLYWAY_URL".equals(varName)) {
                            System.setProperty("FLYWAY_USERNAME", parts[0]);
                            System.setProperty("spring.flyway.user", parts[0]);
                        }
                    }
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        if ("DATABASE_URL".equals(varName)) {
                            System.setProperty("DATABASE_PASSWORD", parts[1]);
                            System.setProperty("spring.datasource.password", parts[1]);
                            if (System.getenv("FLYWAY_PASSWORD") == null && System.getProperty("FLYWAY_PASSWORD") == null) {
                                System.setProperty("FLYWAY_PASSWORD", parts[1]);
                                System.setProperty("spring.flyway.password", parts[1]);
                            }
                        } else if ("FLYWAY_URL".equals(varName)) {
                            System.setProperty("FLYWAY_PASSWORD", parts[1]);
                            System.setProperty("spring.flyway.password", parts[1]);
                        }
                    }
                }
            } catch (Exception ignored) {
                String stripped = dbUrl.replaceFirst("^postgres(ql)?://", "");
                String fallback = "jdbc:postgresql://" + stripped;
                System.setProperty(varName, fallback);
                if ("DATABASE_URL".equals(varName)) {
                    System.setProperty("spring.datasource.url", fallback);
                    if (System.getenv("FLYWAY_URL") == null && System.getProperty("FLYWAY_URL") == null) {
                        System.setProperty("FLYWAY_URL", fallback);
                        System.setProperty("spring.flyway.url", fallback);
                    }
                } else if ("FLYWAY_URL".equals(varName)) {
                    System.setProperty("spring.flyway.url", fallback);
                }
            }
        }
    }
}
