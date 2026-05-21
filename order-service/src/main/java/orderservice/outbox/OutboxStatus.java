package orderservice.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
