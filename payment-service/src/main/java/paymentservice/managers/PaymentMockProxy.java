package paymentservice.managers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import paymentservice.entities.Payment;

@RequiredArgsConstructor
@Service
public class PaymentMockProxy implements PaymentProcess {

    private final double SUCCESS_RATE = 99;

    @Override
    public boolean processPayment(Payment p) {
        long t = System.currentTimeMillis() % 100;
        if (t == 0){
            t = 100;
        }

        return t < SUCCESS_RATE;
    }

    @Override
    public boolean processRefund(Payment p) {
        return true;
    }
}
