package com.ledgerline.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {}
    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) {}
    public record UserView(Long id, String email, String role) {}
    public record LoginResponse(String accessToken, UserView user) {}

    public record CreateTransactionRequest(
            @NotBlank @Pattern(regexp = "DEPOSIT|WITHDRAWAL|TRANSFER") String type,
            Long senderAccountId,
            Long receiverAccountId,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 140) String memo) {}

    public record TransactionView(UUID id, String type, Long senderAccountId, Long receiverAccountId,
                                  BigDecimal amount, String status, String memo,
                                  Instant createdAt, Long requestedByUserId,
                                  Long decidedByUserId, Instant decidedAt, UUID reversalOfTransactionId) {}
    public record AccountView(Long id, String accountNumber, BigDecimal balance, String status) {}
    public record LedgerView(Long id, Long accountId, UUID transactionId, String entryType, BigDecimal amount,
                             BigDecimal runningBalance, String entryHash, Instant createdAt) {}
    public record AuditView(Long id, String eventType, String description, String ipAddress,
                            String status, Instant createdAt, String correlationId) {}
    public record VerifyResult(String status, long checkedRows, String lastHash, Long brokenRowId,
                               Instant verifiedAt) {}
    public record Overview(long totalTransactions, long successfulTransactions, long failedTransactions,
                           long pendingTransactions, BigDecimal totalVolume, String apiStatus) {}
    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 12, max = 72) String password,
                                    @NotBlank @Pattern(regexp = "ADMIN|AUTHORISER|ACCOUNT_HOLDER|AUDITOR") String role) {}
    public record RoleUpdateRequest(@NotBlank @Pattern(regexp = "ADMIN|AUTHORISER|ACCOUNT_HOLDER|AUDITOR") String role) {}
    public record CreateAccountRequest(@NotNull Long ownerUserId) {}
    public record FreezeAccountRequest(@NotBlank @Size(max = 240) String reason) {}
    public record ReverseTransactionRequest(@NotBlank @Size(max = 140) String reason) {}
    public record Message(String message) {}
    public record ErrorBody(String code, String message, Instant timestamp, String correlationId) {}
}
