package orderservice.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists saga lifecycle transitions in their own transactions so that a
 * rollback of a step (or of the entire saga) never erases the audit trail.
 * Each method uses {@link Propagation#REQUIRES_NEW}.
 */
@Service
@RequiredArgsConstructor
public class SagaStateService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final SagaInstanceRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaInstance begin(String sagaType) {
        SagaInstance instance = new SagaInstance(sagaType);
        return repository.save(instance);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCurrentStep(UUID sagaId, String stepName) {
        repository.findById(sagaId).ifPresent(s -> s.setCurrentStep(stepName));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID sagaId) {
        repository.findById(sagaId).ifPresent(s -> s.setStatus(SagaStatus.COMPLETED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensating(UUID sagaId, String errorMessage) {
        repository.findById(sagaId).ifPresent(s -> {
            s.setStatus(SagaStatus.COMPENSATING);
            s.setErrorMessage(truncate(errorMessage));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensated(UUID sagaId) {
        repository.findById(sagaId).ifPresent(s -> s.setStatus(SagaStatus.COMPENSATED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID sagaId, String errorMessage) {
        repository.findById(sagaId).ifPresent(s -> {
            s.setStatus(SagaStatus.FAILED);
            s.setErrorMessage(truncate(errorMessage));
        });
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > ERROR_MESSAGE_MAX_LENGTH ? s.substring(0, ERROR_MESSAGE_MAX_LENGTH) : s;
    }
}
