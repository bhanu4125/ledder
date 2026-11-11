package com.ledgerline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
@EnableScheduling
public class LedgerLineApplication {
    public static void main(String[] args) {
        DatabaseUrlNormalizer.normalize();
        SpringApplication.run(LedgerLineApplication.class, args);
    }
}
