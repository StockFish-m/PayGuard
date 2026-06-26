package com.payguard.engine.controller.reconciliation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payguard.engine.dto.PayOsResponseDTO;
import com.payguard.engine.dto.PayOsTransactionDTO;

@RestController
@RequestMapping("")
public class MockPayOsController {

    @GetMapping("/v2/payment-requests")
    public ResponseEntity<PayOsResponseDTO> mockPayOsApi() {

        // 1. Khớp đơn số 4: payOS báo thu về 120.000đ thành công -> Đọc trúng PENDING
        // dưới DB -> Cứu đơn!
        PayOsTransactionDTO txn4 = new PayOsTransactionDTO();
        txn4.setOrderCode(4L); // Map trúng với 'TXN-004' (Hàm String.valueOf trong Service sẽ phân tách sạch
        txn4.setAmount(120000L);
        txn4.setStatus("PAID");

        // 2. Khớp đơn số 5: payOS báo thu về 80.000đ -> Đọc trúng SUCCESS dưới DB -> Bỏ
        // qua an toàn!
        PayOsTransactionDTO txn5 = new PayOsTransactionDTO();
        txn5.setOrderCode(5L);
        txn5.setAmount(80000L);
        txn5.setStatus("PAID");

        // 3. Khớp đơn số 6: payOS báo thực tế chỉ thu được 150.000đ (Trong khi DB đòi
        // 200.000đ) -> Chuyển trạng thái lệch tiền!
        PayOsTransactionDTO txn6 = new PayOsTransactionDTO();
        txn6.setOrderCode(6L);
        txn6.setAmount(150000L); // Cố tình làm lệch số tiền để kích hoạt kịch bản E
        txn6.setStatus("PAID");

        // Đóng gói mảng 3 tầng chuẩn chỉnh theo đúng kiến trúc DTO mới
        PayOsResponseDTO mockResponse = new PayOsResponseDTO();
        mockResponse.setCode("00");
        mockResponse.setDesc("success");
        mockResponse.setData(List.of(txn4, txn5, txn6));

        return ResponseEntity.ok(mockResponse);
    }
}
