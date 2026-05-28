package orderservice.saga;

public enum SagaStatus {

    STARTED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
