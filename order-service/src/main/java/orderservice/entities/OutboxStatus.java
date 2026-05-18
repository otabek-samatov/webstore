package orderservice.entities;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
