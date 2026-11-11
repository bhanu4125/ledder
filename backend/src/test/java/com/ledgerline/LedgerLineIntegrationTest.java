package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.domain.LedgerUser;
import com.ledgerline.domain.Role;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.JwtService;
import com.ledgerline.service.Hashing;
import java.sql.SQLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerLineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledgerline").withUsername("ledgerline").withPassword("ledgerline-test")
            .withInitScript("test-postgres-init.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledgerEntries;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Value("${ledgerline.frontend-origin}") String frontendOrigin;
    @Value("${ledgerline.jwt.secret}") String jwtSecret;

    @Test
    @Order(1)
    void ownershipAndRoleControlsRejectCrossAccountAndAuditorWrites() throws Exception {
        String holder = token("holder@ledgerline.demo", "LedgerLine-Holder-2026!");
        long otherAccount = accounts.findByOwnerIdOrderById( users.findByEmailIgnoreCase("holder2@ledgerline.demo").orElseThrow().getId())
                .get(0).getId();
        ResponseEntity<String> idor = send("/api/accounts/" + otherAccount, "GET", null, holder, null);
        assertEquals(HttpStatus.FORBIDDEN, idor.getStatusCode());

        JsonNode holderAccounts = mapper.readTree(send("/api/accounts", "GET", null, holder, null).getBody());
        long ownAccount = holderAccounts.get(0).get("id").asLong();
        String withdrawal = mapper.writeValueAsString(mapper.createObjectNode()
                .put("type", "WITHDRAWAL").put("senderAccountId", otherAccount)
                .put("amount", "1.00"));
        assertEquals(HttpStatus.FORBIDDEN, send("/api/transactions", "POST", withdrawal, holder, UUID.randomUUID().toString()).getStatusCode());

        String auditor = token("auditor@ledgerline.demo", "LedgerLine-Audit-2026!");
        String transfer = mapper.writeValueAsString(mapper.createObjectNode().put("type", "TRANSFER")
                .put("senderAccountId", ownAccount).put("receiverAccountId", otherAccount).put("amount", "1.00"));
        assertEquals(HttpStatus.FORBIDDEN, send("/api/transactions", "POST", transfer, auditor, UUID.randomUUID().toString()).getStatusCode());
    }

    @Test
    @Order(2)
    void tamperedJwtCannotEscalateRole() throws Exception {
        String token = token("auditor@ledgerline.demo", "LedgerLine-Audit-2026!");
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"role\":\"AUDITOR\"", "\"role\":\"ADMIN\"");
        String changed = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        ResponseEntity<String> response = send("/api/users", "GET", null, changed, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().contains("ADMIN"));
    }

    @Test
    @Order(3)
    void duplicateTransactionIsReturnedOnceAndChangedBodyConflicts() throws Exception {
        String token = token("holder@ledgerline.demo", "LedgerLine-Holder-2026!");
        JsonNode accountRows = mapper.readTree(send("/api/accounts", "GET", null, token, null).getBody());
        long source = accountRows.get(0).get("id").asLong();
        long target = accountRows.get(1).get("id").asLong();
        String key = UUID.randomUUID().toString();
        String body = mapper.writeValueAsString(mapper.createObjectNode().put("type", "TRANSFER")
                .put("senderAccountId", source).put("receiverAccountId", target).put("amount", "1.25"));
        long before = ledgerEntries.count();
        ResponseEntity<String> first = send("/api/transactions", "POST", body, token, key);
        ResponseEntity<String> repeated = send("/api/transactions", "POST", body, token, key);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(first.getBody(), repeated.getBody());
        assertEquals(before + 2, ledgerEntries.count());

        String changed = body.replace("1.25", "2.25");
        assertEquals(HttpStatus.CONFLICT, send("/api/transactions", "POST", changed, token, key).getStatusCode());

        String concurrentKey = UUID.randomUUID().toString();
        long concurrentBefore = ledgerEntries.count();
        var executor = Executors.newFixedThreadPool(2);
        var concurrent = new ArrayList<java.util.concurrent.Future<ResponseEntity<String>>>();
        for (int i = 0; i < 2; i++) {
            concurrent.add(executor.submit(() -> send("/api/transactions", "POST", body, token, concurrentKey)));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        ResponseEntity<String> left = concurrent.get(0).get();
        ResponseEntity<String> right = concurrent.get(1).get();
        assertEquals(HttpStatus.CREATED, left.getStatusCode());
        assertEquals(left.getBody(), right.getBody());
        assertEquals(concurrentBefore + 2, ledgerEntries.count());
    }

    @Test
    @Order(4)
    void concurrentWithdrawalsNeverOverdrawAnAccount() throws Exception {
        String token = token("holder2@ledgerline.demo", "LedgerLine-Holder2-2026!");
        long accountId = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder2@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        var executor = Executors.newFixedThreadPool(50);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String body = mapper.createObjectNode().put("type", "WITHDRAWAL")
                    .put("senderAccountId", accountId).put("amount", "2500.00").toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/transactions")))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            results.add(executor.submit(() -> client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(90, TimeUnit.SECONDS));
        long created = 0;
        long conflict = 0;
        for (var result : results) {
            int status = result.get();
            if (status == 201) created++;
            else if (status == 409) conflict++;
            else fail("Unexpected withdrawal response: " + status);
        }
        assertEquals(5, created);
        assertEquals(45, conflict);
        assertEquals(0, accounts.findById(accountId).orElseThrow().getBalance().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    @Order(5)
    void loginFailuresAreGenericAndLockTheAccount() {
        String email = "lockout-" + UUID.randomUUID() + "@ledgerline.demo";
        users.saveAndFlush(new LedgerUser(email, passwordEncoder.encode("Strong-Example-2026!"), Role.ACCOUNT_HOLDER));
        String firstBody = null;
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> failed = login(email, "wrong-password");
            assertEquals(HttpStatus.UNAUTHORIZED, failed.getStatusCode());
            if (firstBody == null) firstBody = failed.getBody();
            else assertEquals(firstBody, failed.getBody());
        }
        assertEquals(HttpStatus.UNAUTHORIZED, login(email, "Strong-Example-2026!").getStatusCode());
    }

    @Test
    @Order(6)
    void seededLedgerPassesHashChainVerification() throws Exception {
        String token = token("auditor@ledgerline.demo", "LedgerLine-Audit-2026!");
        ResponseEntity<String> response = send("/api/ledger/verify", "POST", "{}", token, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("VERIFIED", mapper.readTree(response.getBody()).get("status").asText());
    }

    @Test
    @Order(7)
    void invalidAmountsAndExpiredTokensAreRejected() throws Exception {
        String holder = token("holder@ledgerline.demo", "LedgerLine-Holder-2026!");
        long accountId = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        for (String amount : List.of("-1.00", "0", "99999999999999999999.00")) {
            String body = "{\"type\":\"WITHDRAWAL\",\"senderAccountId\":" + accountId + ",\"amount\":" + amount + "}";
            assertEquals(HttpStatus.BAD_REQUEST,
                    send("/api/transactions", "POST", body, holder, UUID.randomUUID().toString()).getStatusCode());
        }
        String malformed = "{\"type\":\"WITHDRAWAL\",\"senderAccountId\":" + accountId + ",\"amount\":not-a-number}";
        assertEquals(HttpStatus.BAD_REQUEST,
                send("/api/transactions", "POST", malformed, holder, UUID.randomUUID().toString()).getStatusCode());

        String expired = io.jsonwebtoken.Jwts.builder().issuer("ledgerline").subject("1")
                .claim("role", "ADMIN").issuedAt(java.util.Date.from(java.time.Instant.now().minusSeconds(120)))
                .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertEquals(HttpStatus.UNAUTHORIZED, send("/api/users", "GET", null, expired, null).getStatusCode());
    }

    @Test
    @Order(8)
    void systemWideLedgerDebitsEqualCreditsAndBalancesMatchLatestRows() {
        var totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS debits,
                       COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) AS credits
                FROM ledger_entries
                """);
        assertEquals(0, ((java.math.BigDecimal) totals.get("debits"))
                .compareTo((java.math.BigDecimal) totals.get("credits")));
        Integer staleBalances = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts a
                WHERE EXISTS (SELECT 1 FROM ledger_entries e WHERE e.account_id = a.id)
                  AND a.balance <> (SELECT e.running_balance FROM ledger_entries e
                                    WHERE e.account_id = a.id ORDER BY e.id DESC LIMIT 1)
                """, Integer.class);
        assertEquals(0, staleBalances);
    }

    @Test
    @Order(9)
    void runtimeDatabaseRoleCannotUpdateOrDeleteAppendOnlyTables() {
        for (String table : List.of("ledger_entries", "audit_log")) {
            Boolean updateAllowed = jdbc.queryForObject(
                    "SELECT has_table_privilege('ledgerline_runtime', ?, 'UPDATE')", Boolean.class, table);
            Boolean deleteAllowed = jdbc.queryForObject(
                    "SELECT has_table_privilege('ledgerline_runtime', ?, 'DELETE')", Boolean.class, table);
            assertFalse(updateAllowed);
            assertFalse(deleteAllowed);
            assertDeniedAsRuntime("UPDATE " + table + " SET " + (table.equals("ledger_entries") ? "amount = amount" : "description = description") + " WHERE id = 1");
            assertDeniedAsRuntime("DELETE FROM " + table + " WHERE id = 1");
        }
    }

    @Test
    @Order(10)
    void accountCreationDtoIgnoresMassAssignmentFields() throws Exception {
        String admin = token("admin@ledgerline.demo", "LedgerLine-Admin-2026!");
        long ownerId = users.findByEmailIgnoreCase("holder@ledgerline.demo").orElseThrow().getId();
        String malicious = mapper.createObjectNode().put("ownerUserId", ownerId)
                .put("balance", "999999.00").put("status", "FROZEN").put("system", true).toString();
        ResponseEntity<String> created = send("/api/accounts", "POST", malicious, admin, null);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        long id = mapper.readTree(created.getBody()).get("id").asLong();
        var account = accounts.findById(id).orElseThrow();
        assertEquals(0, account.getBalance().compareTo(java.math.BigDecimal.ZERO));
        assertFalse(account.isSystem());
        assertEquals("ACTIVE", account.getStatus());
    }

    @Test
    @Order(11)
    void accountFreezeBlocksTransactionsAndStatementExportIsOwnershipScoped() throws Exception {
        String admin = token("admin@ledgerline.demo", "LedgerLine-Admin-2026!");
        String holder = token("holder2@ledgerline.demo", "LedgerLine-Holder2-2026!");
        long accountId = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder2@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        String correlationId = UUID.randomUUID().toString();
        HttpHeaders freezeHeaders = jsonHeaders(admin, null);
        freezeHeaders.set("X-Correlation-ID", correlationId);
        ResponseEntity<String> frozen = rest.exchange(url("/api/accounts/" + accountId + "/freeze"),
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "suspected credential compromise"), freezeHeaders),
                String.class);
        assertEquals(HttpStatus.OK, frozen.getStatusCode());
        assertEquals(correlationId, frozen.getHeaders().getFirst("X-Correlation-ID"));
        assertEquals(correlationId, jdbc.queryForObject(
                "SELECT correlation_id FROM audit_log WHERE event_type = 'ACCOUNT_FROZEN' ORDER BY id DESC LIMIT 1",
                String.class));

        String withdrawal = mapper.createObjectNode().put("type", "WITHDRAWAL")
                .put("senderAccountId", accountId).put("amount", "1.00").toString();
        assertEquals(HttpStatus.CONFLICT, send("/api/transactions", "POST", withdrawal, holder,
                UUID.randomUUID().toString()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                send("/api/accounts/" + accountId + "/freeze", "POST",
                        "{\"reason\":\"not an administrator\"}", holder, null).getStatusCode());

        assertEquals(HttpStatus.OK, rest.exchange(url("/api/accounts/" + accountId + "/unfreeze"),
                HttpMethod.POST, new HttpEntity<>("{}", jsonHeaders(admin, null)), String.class).getStatusCode());
        ResponseEntity<String> statement = rest.exchange(url("/api/accounts/" + accountId + "/statement.csv"),
                HttpMethod.GET, new HttpEntity<>(jsonHeaders(holder, null)), String.class);
        assertEquals(HttpStatus.OK, statement.getStatusCode());
        assertTrue(statement.getHeaders().getContentType().toString().startsWith("text/csv"));
        assertTrue(statement.getBody().startsWith("entry_id,transaction_id,entry_type"));

        String otherHolder = token("holder@ledgerline.demo", "LedgerLine-Holder-2026!");
        long otherAccountId = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange(
                url("/api/accounts/" + otherAccountId + "/statement.csv"), HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(holder, null)), String.class).getStatusCode());
    }

    @Test
    @Order(12)
    void reversalCreatesBalancedCompensatingTransactionAndIsIdempotent() throws Exception {
        String holder = token("holder@ledgerline.demo", "LedgerLine-Holder-2026!");
        long source = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        long target = accounts.findByOwnerIdOrderById(
                users.findByEmailIgnoreCase("holder2@ledgerline.demo").orElseThrow().getId()).get(0).getId();
        String transfer = mapper.createObjectNode().put("type", "TRANSFER")
                .put("senderAccountId", source).put("receiverAccountId", target).put("amount", "1.00").toString();
        ResponseEntity<String> created = send("/api/transactions", "POST", transfer, holder,
                UUID.randomUUID().toString());
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        UUID originalId = UUID.fromString(mapper.readTree(created.getBody()).get("id").asText());

        String authoriser = token("authoriser@ledgerline.demo", "LedgerLine-Approve-2026!");
        String reason = "{\"reason\":\"correction requested by customer\"}";
        String key = UUID.randomUUID().toString();
        long before = ledgerEntries.count();
        ResponseEntity<String> reversed = send("/api/transactions/" + originalId + "/reverse",
                "POST", reason, authoriser, key);
        assertEquals(HttpStatus.CREATED, reversed.getStatusCode(), reversed.getBody());
        JsonNode reversal = mapper.readTree(reversed.getBody());
        assertEquals("REVERSAL", reversal.get("type").asText());
        assertEquals(originalId.toString(), reversal.get("reversalOfTransactionId").asText());
        assertEquals("REVERSED", jdbc.queryForObject(
                "SELECT status FROM transactions WHERE id = ?", String.class, originalId));
        assertEquals(before + 2, ledgerEntries.count());

        ResponseEntity<String> replay = send("/api/transactions/" + originalId + "/reverse",
                "POST", reason, authoriser, key);
        assertEquals(reversed.getBody(), replay.getBody());
        assertEquals(HttpStatus.CONFLICT, send("/api/transactions/" + originalId + "/reverse",
                "POST", reason, authoriser, UUID.randomUUID().toString()).getStatusCode());
        String auditor = token("auditor@ledgerline.demo", "LedgerLine-Audit-2026!");
        assertEquals(HttpStatus.FORBIDDEN, send("/api/transactions/" + originalId + "/reverse",
                "POST", reason, auditor, UUID.randomUUID().toString()).getStatusCode());
    }

    @Test
    @Order(13)
    void tamperingWithHistoricalLedgerDataReportsTheFirstBrokenRow() throws Exception {
        Long firstId = jdbc.queryForObject("SELECT id FROM ledger_entries ORDER BY id LIMIT 1", Long.class);
        assertNotNull(firstId);
        jdbc.update("UPDATE ledger_entries SET amount = amount + 1 WHERE id = ?", firstId);
        String token = token("auditor@ledgerline.demo", "LedgerLine-Audit-2026!");
        ResponseEntity<String> response = send("/api/ledger/verify", "POST", "{}", token, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode result = mapper.readTree(response.getBody());
        assertEquals("TAMPERED", result.get("status").asText());
        assertEquals(firstId.longValue(), result.get("brokenRowId").asLong());
    }

    private void assertDeniedAsRuntime(String sql) {
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET ROLE ledgerline_runtime");
                try {
                    statement.execute(sql);
                    fail("Runtime database role unexpectedly executed " + sql);
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
            return null;
        }));
    }

    private String token(String email, String password) throws Exception {
        ResponseEntity<String> response = login(email, password);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return mapper.readTree(response.getBody()).get("accessToken").asText();
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.exchange(url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password),
                        jsonHeaders(null, null)), String.class);
    }

    private ResponseEntity<String> send(String path, String method, String body, String token, String key) {
        return rest.exchange(url(path), HttpMethod.valueOf(method),
                new HttpEntity<>(body, jsonHeaders(token, key)), String.class);
    }

    private HttpHeaders jsonHeaders(String token, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        headers.setOrigin(frontendOrigin);
        return headers;
    }

    private String url(String path) { return "http://localhost:" + port + path; }
}
