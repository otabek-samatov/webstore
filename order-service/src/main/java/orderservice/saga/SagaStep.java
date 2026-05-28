package orderservice.saga;

/**
 * A single step in an orchestrated saga.
 * <p>
 * {@link #execute(SagaContext)} performs the forward action; if it throws,
 * the orchestrator runs {@link #compensate(SagaContext)} for every step that
 * already succeeded, in reverse order.
 * <p>
 * Compensations should be idempotent and tolerate being called even when the
 * forward action partially succeeded — the orchestrator cannot tell the
 * difference between "step failed without side effects" and "step succeeded
 * but recording it failed".
 */
public interface SagaStep {

    String name();

    void execute(SagaContext context);

    default void compensate(SagaContext context) {
        // no-op for read-only or last steps
    }
}
