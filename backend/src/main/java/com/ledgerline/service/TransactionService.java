package com.ledgerline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import com.ledgerline.web.ApiModels.CreateTransactionRequest;
import com.ledgerline.web.ApiModels.TransactionView;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final String ENDPOINT = "/api/transactions";
    private final AccountRepository accounts;
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final IdempotencyRepository idempotency;
    private final LedgerService ledger;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final BigDecimal approvalThreshold;

    public TransactionService(AccountRepository accounts, UserRepository users, TransactionRepository transactions,
                              IdempotencyRepository idempotency, LedgerService ledger, AuditService audit,
                              ObjectMapper objectMapper, JdbcTemplate jdbc,
                              @Value("${ledgerline.authorisation-threshold}") BigDecimal approvalThreshold) {
        this.accounts=accounts; this.users=users; this.transactions=transactions; this.idempotency=idempotency;
        this.ledger=ledger; this.audit=audit; this.objectMapper=objectMapper; this.jdbc=jdbc;
        this.approvalThreshold=approvalThreshold;
    }

    @Transactional
    public TransactionView create(CreateTransactionRequest request, String rawKey, Long userId, String ip) {
        if (rawKey == null || !rawKey.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw ApiException.badRequest("A valid Idempotency-Key header is required");
        }
        String endpoint = ENDPOINT + "/" + userId;
        lockKey(rawKey + endpoint);
        String requestHash = hashRequest(request);
        Optional<IdempotencyRecord> prior = idempotency.findByRequestKeyAndEndpoint(rawKey, endpoint);
        if (prior.isPresent()) {
            IdempotencyRecord record = prior.get();
            if (!Hashing.matches(record.getRequestHash(), requestHash)) {
                throw ApiException.conflict("Idempotency key was already used with a different request");
            }
            try { return objectMapper.readValue(record.getResponseBody(), TransactionView.class); }
            catch (JsonProcessingException exception) { throw new IllegalStateException("Stored idempotency response is invalid", exception); }
        }

        Account sender = null;
        Account receiver = null;
        Account system = null;
        switch (request.type()) {
            case "DEPOSIT" -> {
                if (request.receiverAccountId() == null) throw ApiException.badRequest("Destination account is required");
                Long systemId = systemAccountId();
                Map<Long, Account> locked = lockIds(List.of(request.receiverAccountId(), systemId));
                receiver = ownedOrForbidden(locked.get(request.receiverAccountId()), userId);
                system = existing(locked.get(systemId));
            }
            case "WITHDRAWAL" -> {
                if (request.senderAccountId() == null) throw ApiException.badRequest("Source account is required");
                Long systemId = systemAccountId();
                Map<Long, Account> locked = lockIds(List.of(request.senderAccountId(), systemId));
                sender = ownedOrForbidden(locked.get(request.senderAccountId()), userId);
                system = existing(locked.get(systemId));
            }
            case "TRANSFER" -> {
                if (request.senderAccountId() == null || request.receiverAccountId() == null) {
                    throw ApiException.badRequest("Source and destination accounts are required");
                }
                if (Objects.equals(request.senderAccountId(), request.receiverAccountId())) {
                    throw ApiException.badRequest("A transfer must use two different accounts");
                }
                Map<Long, Account> locked = lockIds(List.of(request.senderAccountId(), request.receiverAccountId()));
                sender = ownedOrForbidden(locked.get(request.senderAccountId()), userId);
                receiver = existing(locked.get(request.receiverAccountId()));
                if (receiver.getOwner() == null || receiver.isSystem()) throw ApiException.badRequest("Invalid destination account");
            }
            default -> throw ApiException.badRequest("Unsupported transaction type");
        }
        if (sender != null) active(sender);
        if (receiver != null) active(receiver);
        boolean pending = request.amount().compareTo(approvalThreshold) > 0;
        String scopedKey = userId + ":" + rawKey;
        LedgerUser actor = users.findById(userId).orElseThrow(ApiException::unauthorized);
        FinancialTransaction transaction = transactions.saveAndFlush(new FinancialTransaction(
                request.type(), sender, receiver, request.amount(), pending ? "PENDING" : "COMPLETED",
                scopedKey, actor, request.memo()));
        if (!pending) {
            switch (request.type()) {
                case "DEPOSIT" -> ledger.postPair(system, receiver, transaction);
                case "WITHDRAWAL" -> ledger.postPair(sender, system, transaction);
                case "TRANSFER" -> ledger.postPair(sender, receiver, transaction);
                default -> throw new IllegalStateException("Validated transaction type changed");
            }
        }
        audit.record("TRANSACTION_" + transaction.getStatus(), actor,
                "Transaction " + transaction.getId() + " " + transaction.getStatus().toLowerCase(), ip, transaction.getStatus());
        TransactionView response = view(transaction);
        try {
            idempotency.save(new IdempotencyRecord(rawKey, endpoint, requestHash,
                    objectMapper.writeValueAsString(response), HttpStatus.CREATED.value()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode transaction response", exception);
        }
        return response;
    }

    @Transactional
    public TransactionView decide(UUID id, boolean approve, Long actorId, String ip) {
        FinancialTransaction transaction = transactions.findLockedById(id).orElseThrow(ApiException::notFound);
        if (!"PENDING".equals(transaction.getStatus())) throw ApiException.conflict("Transaction is no longer pending");
        LedgerUser actor = users.findById(actorId).orElseThrow(ApiException::unauthorized);
        if (approve) {
            Long systemId = ("DEPOSIT".equals(transaction.getType()) || "WITHDRAWAL".equals(transaction.getType()))
                    ? systemAccountId() : null;
            Map<Long, Account> locked = lockIds(Arrays.asList(
                    transaction.getSender() == null ? null : transaction.getSender().getId(),
                    transaction.getReceiver() == null ? null : transaction.getReceiver().getId(), systemId));
            Account sender = transaction.getSender() == null ? null : locked.get(transaction.getSender().getId());
            Account receiver = transaction.getReceiver() == null ? null : locked.get(transaction.getReceiver().getId());
            Account system = systemId == null ? null : existing(locked.get(systemId));
            switch (transaction.getType()) {
                case "DEPOSIT" -> ledger.postPair(system, receiver, transaction);
                case "WITHDRAWAL" -> ledger.postPair(sender, system, transaction);
                case "TRANSFER" -> ledger.postPair(sender, receiver, transaction);
                default -> throw new IllegalStateException("Unexpected persisted transaction type");
            }
        }
        transaction.decide(actor, approve);
        audit.record(approve ? "TRANSACTION_APPROVED" : "TRANSACTION_REJECTED", actor,
                "Transaction " + id + " " + (approve ? "approved" : "rejected"), ip, approve ? "APPROVED" : "REJECTED");
        return view(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionView> list(Long userId, boolean all) {
        List<FinancialTransaction> rows = all ? transactions.findTop100ByOrderByCreatedAtDesc()
                : transactions.findVisibleToUser(userId, PageRequest.of(0, 100));
        return rows.stream().map(TransactionService::view).toList();
    }

    public static TransactionView view(FinancialTransaction transaction) {
        return new TransactionView(transaction.getId(), transaction.getType(),
                transaction.getSender() == null ? null : transaction.getSender().getId(),
                transaction.getReceiver() == null ? null : transaction.getReceiver().getId(),
                transaction.getAmount(), transaction.getStatus(), transaction.getMemo(),
                transaction.getCreatedAt(), transaction.getRequestedBy().getId(),
                transaction.getDecidedBy() == null ? null : transaction.getDecidedBy().getId(),
                transaction.getDecidedAt());
    }

    private Map<Long, Account> lockIds(Collection<Long> ids) {
        TreeSet<Long> sorted = new TreeSet<>();
        ids.stream().filter(Objects::nonNull).forEach(sorted::add);
        Map<Long, Account> result = new HashMap<>();
        for (Long id : sorted) result.put(id, accounts.findLockedById(id).orElse(null));
        return result;
    }
    private Account ownedOrForbidden(Account account, Long ownerId) {
        Account found = existing(account);
        if (found.getOwner() == null || !found.getOwner().getId().equals(ownerId)) throw ApiException.forbidden();
        return found;
    }
    private Account existing(Account account) {
        if (account == null) throw ApiException.notFound();
        return account;
    }
    private Long systemAccountId() {
        return accounts.findSystemId().orElseThrow(() -> new IllegalStateException("System account is missing"));
    }
    private void active(Account account) {
        if (!"ACTIVE".equals(account.getStatus())) throw ApiException.conflict("Account is not active");
    }
    private String hashRequest(CreateTransactionRequest request) {
        try { return Hashing.sha256(objectMapper.writeValueAsString(request)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not encode request", exception); }
    }
    private void lockKey(String key) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")) {
                statement.setInt(1, 20261112); statement.setString(2, key); statement.execute();
            }
            return null;
        });
    }
}
