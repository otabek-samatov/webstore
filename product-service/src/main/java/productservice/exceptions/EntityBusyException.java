package productservice.exceptions;

public class EntityBusyException extends RuntimeException {
    public EntityBusyException(String message) {
        super(message);
    }
}
