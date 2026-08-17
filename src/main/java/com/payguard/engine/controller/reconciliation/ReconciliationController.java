package com.payguard.engine.controller.reconciliation;

import com.payguard.engine.service.reconciliation.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation")

public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    // Injection tầng Service vào Controller
    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Endpoint chủ động kích hoạt tiến trình đối soát đơn hàng với payOS
     */
    @PostMapping("/trigger")
    public ResponseEntity<String> triggerReconciliation() {
        // Kích hoạt đường ống dẫn dữ liệu chạy ngầm
        reconciliationService.runReconciliation();

        return ResponseEntity.ok("Reconciliation process triggered successfully! Please check IDE logs.");
    }

}
