package com.ledgerline.web;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import com.ledgerline.service.*;
import com.ledgerline.web.ApiModels.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditEventRepository auditEvents;
    private final SecurityEventRepository securityEvents;
    private final TransactionService transactionService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public ApiController(UserRepository users, AccountRepository accounts, TransactionRepository transactions,
                         AuditEventRepository auditEvents, SecurityEventRepository securityEvents,
                         TransactionService transactionService,
                         LedgerService ledgerService, AuditService auditService, PasswordEncoder passwordEncoder) {
        this.users=users; this.accounts=accounts; this.transactions=transactions;
        this.auditEvents=auditEvents; this.securityEvents=securityEvents; this.transactionService=transactionService;
        this.ledgerService=ledgerService; this.auditService=auditService; this.passwordEncoder=passwordEncoder;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "authorisation", "UP", "duplicateProtection", "UP");
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public UserView profile(Authentication authentication) {
        LedgerUser user = currentUser(authentication);
        return new UserView(user.getId(), user.getEmail(), user.getRole().name());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public List<AccountView> accounts(Authentication authentication) {
        LedgerUser actor = currentUser(authentication);
        List<Account> rows = actor.getRole() == Role.ACCOUNT_HOLDER
                ? accounts.findByOwnerIdOrderById(actor.getId())
                : accounts.findAll().stream().filter(account -> !account.isSystem()).toList();
        return rows.stream().map(account -> new AccountView(account.getId(), account.getAccountNumber(),
                account.getBalance(), account.getStatus())).toList();
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public AccountView account(@PathVariable Long id, Authentication authentication) {
        Account account = accounts.findById(id).orElseThrow(ApiException::notFound);
        LedgerUser actor = currentUser(authentication);
        if (account.isSystem()) throw ApiException.notFound();
        if (actor.getRole() == Role.ACCOUNT_HOLDER
                && (account.getOwner() == null || !account.getOwner().getId().equals(actor.getId()))) {
            throw ApiException.forbidden();
        }
        return new AccountView(account.getId(), account.getAccountNumber(), account.getBalance(), account.getStatus());
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AccountView createAccount(@Valid @RequestBody CreateAccountRequest request, Authentication authentication,
                                     HttpServletRequest servletRequest) {
        LedgerUser owner = users.findById(request.ownerUserId()).orElseThrow(ApiException::notFound);
        Account account = accounts.saveAndFlush(new Account(owner,
                "LL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                BigDecimal.ZERO.setScale(2), false));
        auditService.record("ACCOUNT_CREATED", currentUser(authentication),
                "Account " + account.getAccountNumber() + " created", ip(servletRequest), "SUCCESS");
        return new AccountView(account.getId(), account.getAccountNumber(), account.getBalance(), account.getStatus());
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public TransactionView createTransaction(@Valid @RequestBody CreateTransactionRequest request,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                             Authentication authentication, HttpServletRequest servletRequest) {
        return transactionService.create(request, key, userId(authentication), ip(servletRequest));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public List<TransactionView> transactions(Authentication authentication,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String search) {
        LedgerUser actor = currentUser(authentication);
        boolean all = actor.getRole() != Role.ACCOUNT_HOLDER;
        return transactionService.list(actor.getId(), all).stream()
                .filter(row -> status == null || status.isBlank() || row.status().equalsIgnoreCase(status))
                .filter(row -> type == null || type.isBlank() || row.type().equalsIgnoreCase(type))
                .filter(row -> search == null || search.isBlank() || row.id().toString().contains(search))
                .toList();
    }

    @GetMapping("/authorisations")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public List<TransactionView> authorisations(Authentication authentication) {
        LedgerUser actor = currentUser(authentication);
        return transactionService.list(actor.getId(), actor.getRole() != Role.ACCOUNT_HOLDER)
                .stream().filter(row -> "PENDING".equals(row.status()) || row.decidedByUserId() != null).toList();
    }

    @PostMapping("/authorisations/{id}/approve")
    @PreAuthorize("hasRole('AUTHORISER')")
    public TransactionView approve(@PathVariable UUID id, Authentication authentication, HttpServletRequest request) {
        return transactionService.decide(id, true, userId(authentication), ip(request));
    }

    @PostMapping("/authorisations/{id}/reject")
    @PreAuthorize("hasRole('AUTHORISER')")
    public TransactionView reject(@PathVariable UUID id, Authentication authentication, HttpServletRequest request) {
        return transactionService.decide(id, false, userId(authentication), ip(request));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public List<LedgerView> ledger(Authentication authentication) {
        LedgerUser actor = currentUser(authentication);
        return ledgerService.list(actor.getId(), actor.getRole() != Role.ACCOUNT_HOLDER);
    }

    @PostMapping("/ledger/verify")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public VerifyResult verifyLedger(Authentication authentication, HttpServletRequest request) {
        VerifyResult result = ledgerService.verify();
        auditService.record("LEDGER_VERIFICATION", currentUser(authentication),
                "Ledger verification " + result.status() + "; checked " + result.checkedRows() + " entries",
                ip(request), result.status());
        return result;
    }

    @GetMapping("/ledger/verification-status")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public Map<String, Object> ledgerVerificationStatus() {
        return auditEvents.findFirstByEventTypeInOrderByIdDesc(
                        List.of("LEDGER_VERIFICATION", "SCHEDULED_LEDGER_VERIFICATION"))
                .map(event -> Map.<String, Object>of("status", event.getStatus(),
                        "verifiedAt", event.getCreatedAt(), "description", event.getDescription()))
                .orElseGet(() -> Map.of("status", "NOT_RUN", "verifiedAt", "", "description", "No verification has run yet"));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<AuditView> auditLog() {
        return auditEvents.findTop200ByOrderByIdDesc().stream().map(event -> new AuditView(event.getId(),
                event.getEventType(), event.getDescription(), event.getIpAddress(),
                event.getStatus(), event.getCreatedAt())).toList();
    }

    @GetMapping("/security-events")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<Map<String, Object>> securityEvents() {
        return securityEvents.findTop200ByOrderByCreatedAtDesc().stream().map(event -> Map.<String, Object>of(
                "id", event.getId(), "eventType", event.getEventType(), "description", event.getDescription(),
                "ipAddress", event.getIpAddress() == null ? "" : event.getIpAddress(),
                "status", event.getStatus(), "createdAt", event.getCreatedAt())).toList();
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','AUTHORISER','ACCOUNT_HOLDER')")
    public Overview overview(Authentication authentication) {
        LedgerUser actor = currentUser(authentication);
        if (actor.getRole() == Role.ACCOUNT_HOLDER) {
            long completed = transactions.countVisibleToUserByStatus(actor.getId(), "COMPLETED");
            long failed = transactions.countVisibleToUserByStatus(actor.getId(), "FAILED")
                    + transactions.countVisibleToUserByStatus(actor.getId(), "REJECTED");
            long pending = transactions.countVisibleToUserByStatus(actor.getId(), "PENDING");
            long total = transactions.countVisibleToUserByStatus(actor.getId(), null);
            return new Overview(total, completed, failed, pending,
                    transactions.completedVolumeVisibleToUser(actor.getId()), "UP");
        }
        long completed = transactions.countByStatus("COMPLETED");
        return new Overview(transactions.count(), completed,
                transactions.countByStatus("FAILED") + transactions.countByStatus("REJECTED"),
                transactions.countByStatus("PENDING"), transactions.completedVolume(), "UP");
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> users() {
        return users.findAll().stream().map(user -> new UserView(user.getId(), user.getEmail(), user.getRole().name())).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserView createUser(@Valid @RequestBody CreateUserRequest request, Authentication authentication,
                               HttpServletRequest servletRequest) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must be at most 72 UTF-8 bytes");
        }
        if (users.existsByEmailIgnoreCase(request.email())) throw ApiException.conflict("Email is already registered");
        LedgerUser user = users.save(new LedgerUser(request.email(), passwordEncoder.encode(request.password()),
                Role.valueOf(request.role())));
        auditService.record("USER_CREATED", currentUser(authentication), "User " + user.getEmail() + " created",
                ip(servletRequest), "SUCCESS");
        return new UserView(user.getId(), user.getEmail(), user.getRole().name());
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserView changeRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request,
                               Authentication authentication, HttpServletRequest servletRequest) {
        LedgerUser user = users.findById(id).orElseThrow(ApiException::notFound);
        user.setRole(Role.valueOf(request.role()));
        auditService.record("USER_ROLE_CHANGED", currentUser(authentication),
                "Role changed for user " + user.getEmail(), ip(servletRequest), "SUCCESS");
        return new UserView(user.getId(), user.getEmail(), user.getRole().name());
    }

    private LedgerUser currentUser(Authentication authentication) {
        return users.findById(userId(authentication)).orElseThrow(ApiException::unauthorized);
    }
    private Long userId(Authentication authentication) {
        try { return Long.parseLong(authentication.getName()); }
        catch (NumberFormatException exception) { throw ApiException.unauthorized(); }
    }
    private String ip(HttpServletRequest request) { return request.getRemoteAddr(); }
}
