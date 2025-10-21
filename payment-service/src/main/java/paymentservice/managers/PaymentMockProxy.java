package paymentservice.managers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import paymentservice.entities.Payment;

@RequiredArgsConstructor
@Service
public class PaymentMockProxy implements PaymentProcess {

    private final double SUCCESS_RATE = 0.01;

    @Override
    public boolean processPayment(Payment p) {
        double t = 1 / SUCCESS_RATE;

        return System.currentTimeMillis() % t != 0;
    }

    @Override
    public boolean processRefund(Payment p) {
        return true;
    }
}
