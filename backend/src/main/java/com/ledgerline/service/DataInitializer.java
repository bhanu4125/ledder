package com.ledgerline.service;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.LedgerUser;
import com.ledgerline.domain.Role;
import com.ledgerline.domain.FinancialTransaction;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.TransactionRepository;
import com.ledgerline.repository.UserRepository;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TransactionRepository transactions;
    private final LedgerService ledger;
    private final AuditService audit;
    public DataInitializer(UserRepository users, AccountRepository accounts, PasswordEncoder passwordEncoder,
                           TransactionRepository transactions, LedgerService ledger, AuditService audit) {
        this.users=users; this.accounts=accounts; this.passwordEncoder=passwordEncoder;
        this.transactions=transactions; this.ledger=ledger; this.audit=audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("admin@ledgerline.demo", "LedgerLine-Admin-2026!", Role.ADMIN);
        seedUser("authoriser@ledgerline.demo", "LedgerLine-Approve-2026!", Role.AUTHORISER);
        seedUser("holder@ledgerline.demo", "LedgerLine-Holder-2026!", Role.ACCOUNT_HOLDER);
        seedUser("holder2@ledgerline.demo", "LedgerLine-Holder2-2026!", Role.ACCOUNT_HOLDER);
        seedUser("auditor@ledgerline.demo", "LedgerLine-Audit-2026!", Role.AUDITOR);
        LedgerUser holder = users.findByEmailIgnoreCase("holder@ledgerline.demo").orElseThrow();
        LedgerUser holder2 = users.findByEmailIgnoreCase("holder2@ledgerline.demo").orElseThrow();
        if (accounts.count() == 0) {
            accounts.save(new Account(null, "LL-SYSTEM-FLOAT", new BigDecimal("1000000000.00"), true));
            Account first = accounts.save(new Account(holder, "LL-1001-0001", BigDecimal.ZERO.setScale(2), false));
            Account second = accounts.save(new Account(holder, "LL-1001-0002", BigDecimal.ZERO.setScale(2), false));
            Account third = accounts.save(new Account(holder2, "LL-1002-0001", BigDecimal.ZERO.setScale(2), false));
            fundDemoAccount(holder, first, "25000.00");
            fundDemoAccount(holder, second, "8300.50");
            fundDemoAccount(holder2, third, "12500.00");
        }
    }

    private void fundDemoAccount(LedgerUser owner, Account destination, String amount) {
        Account system = accounts.findBySystemTrue().orElseThrow();
        BigDecimal value = new BigDecimal(amount);
        FinancialTransaction opening = transactions.saveAndFlush(new FinancialTransaction("DEPOSIT", system, destination,
                value, "COMPLETED", "seed:opening:" + destination.getAccountNumber(), owner, "Demo opening balance"));
        ledger.postPair(system, destination, opening);
        audit.record("DEMO_ACCOUNT_FUNDED", owner,
                "Demo account " + destination.getAccountNumber() + " initialized", null, "SUCCESS");
    }

    private void seedUser(String email, String password, Role role) {
        if (!users.existsByEmailIgnoreCase(email)) users.save(new LedgerUser(email, passwordEncoder.encode(password), role));
    }
}
