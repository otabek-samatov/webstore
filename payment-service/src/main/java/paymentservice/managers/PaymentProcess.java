package paymentservice.managers;

import paymentservice.entities.Payment;

public interface PaymentProcess {
    boolean processPayment(Payment p);
    boolean processRefund(Payment p);

}
