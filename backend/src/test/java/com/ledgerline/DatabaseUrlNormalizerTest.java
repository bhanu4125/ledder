package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DatabaseUrlNormalizerTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("DATABASE_URL");
        System.clearProperty("DATABASE_USERNAME");
        System.clearProperty("DATABASE_PASSWORD");
        System.clearProperty("FLYWAY_URL");
        System.clearProperty("FLYWAY_USERNAME");
        System.clearProperty("FLYWAY_PASSWORD");
        System.clearProperty("spring.datasource.url");
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
        System.clearProperty("spring.flyway.url");
        System.clearProperty("spring.flyway.user");
        System.clearProperty("spring.flyway.password");
    }

    @Test
    void standardPostgresUrlIsNormalizedToJdbc() {
        System.setProperty("DATABASE_URL", "postgres://ledger_user:secret123@ep-cool-db.render.com:5432/ledgerline_prod");
        DatabaseUrlNormalizer.normalize();

        assertEquals("jdbc:postgresql://ep-cool-db.render.com:5432/ledgerline_prod",
                System.getProperty("DATABASE_URL"));
        assertEquals("jdbc:postgresql://ep-cool-db.render.com:5432/ledgerline_prod",
                System.getProperty("spring.datasource.url"));
        assertEquals("ledger_user", System.getProperty("DATABASE_USERNAME"));
        assertEquals("ledger_user", System.getProperty("spring.datasource.username"));
        assertEquals("secret123", System.getProperty("DATABASE_PASSWORD"));
        assertEquals("secret123", System.getProperty("spring.datasource.password"));
        assertEquals("jdbc:postgresql://ep-cool-db.render.com:5432/ledgerline_prod",
                System.getProperty("FLYWAY_URL"));
        assertEquals("jdbc:postgresql://ep-cool-db.render.com:5432/ledgerline_prod",
                System.getProperty("spring.flyway.url"));
        assertEquals("ledger_user", System.getProperty("FLYWAY_USERNAME"));
        assertEquals("ledger_user", System.getProperty("spring.flyway.user"));
        assertEquals("secret123", System.getProperty("FLYWAY_PASSWORD"));
        assertEquals("secret123", System.getProperty("spring.flyway.password"));
    }

    @Test
    void flywayUrlIsNormalizedIndependently() {
        System.setProperty("FLYWAY_URL", "postgres://owner:ownerpass@db.render.com:5432/ledgerline");
        DatabaseUrlNormalizer.normalize();

        assertEquals("jdbc:postgresql://db.render.com:5432/ledgerline",
                System.getProperty("FLYWAY_URL"));
        assertEquals("jdbc:postgresql://db.render.com:5432/ledgerline",
                System.getProperty("spring.flyway.url"));
        assertEquals("owner", System.getProperty("FLYWAY_USERNAME"));
        assertEquals("ownerpass", System.getProperty("FLYWAY_PASSWORD"));
    }

    @Test
    void postgresqlUrlWithoutCredentialsIsNormalized() {
        System.setProperty("DATABASE_URL", "postgresql://db-host.internal:5432/ledgerline");
        DatabaseUrlNormalizer.normalize();

        assertEquals("jdbc:postgresql://db-host.internal:5432/ledgerline",
                System.getProperty("spring.datasource.url"));
        assertNull(System.getProperty("spring.datasource.username"));
        assertNull(System.getProperty("spring.datasource.password"));
    }

    @Test
    void existingJdbcUrlIsUntouched() {
        System.setProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/ledgerline");
        DatabaseUrlNormalizer.normalize();

        assertNull(System.getProperty("spring.datasource.url"));
    }
}
