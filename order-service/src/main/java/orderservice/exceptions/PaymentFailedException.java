package orderservice.exceptions;

/**
 * Raised by {@code ProcessPaymentStep} when payment-service reports a payment
 * that did not complete successfully. Triggers the saga's compensations
 * (cancel order + release stock).
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
}
