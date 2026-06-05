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
                    "==> [Doi soat] Don hang {} khop hoan toan. status={}, payOSAmount={}, dbAmount={}. Bo qua.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KICH BAN D: Can cap nhat don hang thanh SUCCESS va gui ve cho khach.
        if (("PENDING".equals(dbStatus) || "FAILED".equals(dbStatus)) && payOsAmount == dbAmount) {
            log.warn(
                    "==> [Doi soat] Don hang {} can cap nhat thanh SUCCESS va gui ve cho khach. currentStatus={}, payOSAmount={}, dbAmount={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KICH BAN E: Can dieu tra thu cong vi so tien khong khop.
        if (payOsAmount != dbAmount) {
            long delta = payOsAmount - dbAmount;

            log.warn(
                    "==> [Doi soat] Don hang {} can dieu tra thu cong vi so tien khong khop. status={}, payOSAmount={}, dbAmount={}, delta={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount, delta);
            return;
        }

        log.warn(
                "==> [Doi soat] Don hang {} co amount khop nhung status khong ho tro hoac bi thieu. status={}, payOSAmount={}, dbAmount={}.",
                orderCode, dbStatus, payOsAmount, dbAmount);
    }
}
