package paymentservice.controllers;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import paymentservice.dto.PaymentDto;
import paymentservice.dto.RefundDto;
import paymentservice.entities.Payment;
import paymentservice.entities.Refund;
import paymentservice.managers.PaymentManager;
import paymentservice.mappers.PaymentMapper;
import paymentservice.mappers.RefundMapper;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/v1/payments")
public class PaymentController {

    private final PaymentManager manager;
    private final PaymentMapper mapper;
    private final RefundMapper refundMapper;

    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@Valid @RequestBody PaymentDto dto) {
        Payment p = manager.processPayment(dto);
        return ResponseEntity.ok(mapper.toDto(p));
    }

    @GetMapping("/{paymentID}")
    public ResponseEntity<PaymentDto> getByID(@PathVariable Long paymentID) {
        Payment p = manager.getPaymentById(paymentID);
        return ResponseEntity.ok(mapper.toDto(p));
    }

    @GetMapping("/order/{orderID}")
    public ResponseEntity<PaymentDto> getByOrderID(@PathVariable Long orderID) {
        Payment p = manager.getPaymentByOrderId(orderID);
        return ResponseEntity.ok(mapper.toDto(p));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundDto> createRefund(@Valid @RequestBody RefundDto dto) {
        Refund p = manager.processRefund(dto);
        return ResponseEntity.ok(refundMapper.toDto(p));
    }

    @GetMapping("/refund/{refundID}")
    public ResponseEntity<RefundDto> getRefundByID(@PathVariable Long refundID) {
        Refund p = manager.getRefundById(refundID);
        return ResponseEntity.ok(refundMapper.toDto(p));
    }

    @GetMapping("/refund/payment/{paymentID}")
    public ResponseEntity<RefundDto> getRefundByPaymentID(@PathVariable Long paymentID) {
        Refund p = manager.getRefundByPaymentId(paymentID);
        return ResponseEntity.ok(refundMapper.toDto(p));
    }

}
