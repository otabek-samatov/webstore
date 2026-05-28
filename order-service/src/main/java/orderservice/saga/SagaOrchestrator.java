package orderservice.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generic orchestrator for synchronous orchestration-based sagas.
 * <p>
 * Runs the supplied steps in order. If a step throws, every previously
 * succeeded step has its {@link SagaStep#compensate(SagaContext)} invoked in
 * reverse order before the orchestrator rethrows as
 * {@link SagaExecutionException}. Saga lifecycle is recorded in {@code saga_instance}
 * via {@link SagaStateService} using independent transactions, so the audit
 * trail survives rollback of the caller's transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaStateService stateService;

    public SagaInstance execute(String sagaType, List<SagaStep> steps, SagaContext context) {
        if (StringUtils.hasText(sagaType)) {
            throw new IllegalArgumentException("sagaType is required");
        }

        if (CollectionUtils.isEmpty(steps)) {
            throw new IllegalArgumentException("saga must have at least one step");
        }

        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        SagaInstance instance = stateService.begin(sagaType);
        context.setSagaId(instance.getId());
        log.info("Saga started type={} id={}", sagaType, instance.getId());

        List<SagaStep> executed = new ArrayList<>();
        SagaStep failedAt = null;
        Exception failure = null;

        for (SagaStep step : steps) {
            stateService.updateCurrentStep(instance.getId(), step.name());
            try {
                log.info("Saga step executing sagaId={} step={}", instance.getId(), step.name());
                step.execute(context);
                executed.add(step);
            } catch (Exception e) {
                failedAt = step;
                failure = e;
                log.error("Saga step failed sagaId={} step={} reason={}",
                        instance.getId(), step.name(), e.getMessage(), e);
                break;
            }
        }

        if (failure == null) {
            stateService.markCompleted(instance.getId());
            log.info("Saga completed type={} id={}", sagaType, instance.getId());
            return instance;
        }

        stateService.markCompensating(instance.getId(), failure.getMessage());
        boolean compensationFailed = compensate(executed, context, instance.getId());

        if (compensationFailed) {
            stateService.markFailed(instance.getId(),
                    "Compensation failed after step '" + failedAt.name() + "': " + failure.getMessage());
        } else {
            stateService.markCompensated(instance.getId());
        }

        throw new SagaExecutionException(instance.getId(), failedAt.name(),
                "Saga " + sagaType + " failed at step '" + failedAt.name() + "': " + failure.getMessage(),
                failure);
    }

    private boolean compensate(List<SagaStep> executed, SagaContext context, UUID sagaId) {
        boolean anyFailed = false;
        for (int i = executed.size() - 1; i >= 0; i--) {
            SagaStep step = executed.get(i);
            try {
                log.info("Saga compensating sagaId={} step={}", sagaId, step.name());
                step.compensate(context);
            } catch (Exception e) {
                anyFailed = true;
                log.error("Saga compensation failed sagaId={} step={} reason={}",
                        sagaId, step.name(), e.getMessage(), e);
            }
        }
        return anyFailed;
    }
}
