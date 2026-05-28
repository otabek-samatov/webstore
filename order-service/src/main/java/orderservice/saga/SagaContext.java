package orderservice.saga;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable per-execution context shared between steps of a saga.
 * Steps read inputs that earlier steps published, write outputs for later
 * steps and for compensation handlers, and consult the {@code sagaId} when
 * they need a stable identifier (e.g. as an outbox aggregateId).
 */
public class SagaContext {

    private final Map<String, Object> data = new HashMap<>();
    private UUID sagaId;

    public UUID getSagaId() {
        return sagaId;
    }

    void setSagaId(UUID sagaId) {
        this.sagaId = sagaId;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }
}
