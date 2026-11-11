package com.ledgerline.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LedgerVerificationSchedule {
    private final LedgerService ledger;
    private final AuditService audit;
    public LedgerVerificationSchedule(LedgerService ledger, AuditService audit) {
        this.ledger=ledger; this.audit=audit;
    }
    @Scheduled(fixedDelayString = "${ledgerline.verification-delay-ms:300000}")
    public void verifyPeriodically() {
        var result = ledger.verify();
        audit.record("SCHEDULED_LEDGER_VERIFICATION", null,
                "Scheduled ledger verification " + result.status() + "; checked " + result.checkedRows() + " entries",
                null, result.status());
    }
}
