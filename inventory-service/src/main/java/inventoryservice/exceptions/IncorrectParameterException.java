package inventoryservice.exceptions;


public class IncorrectParameterException extends RuntimeException {
    public IncorrectParameterException(String s) {
        super(s);
    }
}