package orderservice.saga;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SagaExecutionException extends RuntimeException {

    private final UUID sagaId;
    private final String failedStep;

    public SagaExecutionException(UUID sagaId, String failedStep, String message, Throwable cause) {
        super(message, cause);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
    }
}
