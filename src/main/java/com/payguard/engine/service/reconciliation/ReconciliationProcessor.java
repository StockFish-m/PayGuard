package com.payguard.engine.service.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationProcessor.class);

    // Gia lap ham xu ly doi soat cho tung dong du lieu tu payOS.
    public void processRow(String orderCode, long payOsAmount, String dbStatus, long dbAmount) {

        // KICH BAN C: Khop hoan toan.
        if ("SUCCESS".equals(dbStatus) && payOsAmount == dbAmount) {
            log.info(
                    "==> [Reconciliation] Order {} matched completely. status={}, payOSAmount={}, dbAmount={}. Skipping.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KICH BAN D: Can cap nhat don hang thanh SUCCESS va gui ve cho khach.
        if (("PENDING".equals(dbStatus) || "FAILED".equals(dbStatus)) && payOsAmount == dbAmount) {
            log.warn(
                    "==> [Reconciliation] Order {} needs to be updated to SUCCESS and sent to client. currentStatus={}, payOSAmount={}, dbAmount={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KICH BAN E: Can dieu tra thu cong vi so tien khong khop.
        if (payOsAmount != dbAmount) {
            long delta = payOsAmount - dbAmount;

            log.warn(
                    "==> [Reconciliation] Order {} needs manual investigation due to mismatched amount. status={}, payOSAmount={}, dbAmount={}, delta={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount, delta);
            return;
        }

        log.warn(
                "==> [Reconciliation] Order {} has matching amount but unsupported or missing status. status={}, payOSAmount={}, dbAmount={}.",
                orderCode, dbStatus, payOsAmount, dbAmount);
    }
}
