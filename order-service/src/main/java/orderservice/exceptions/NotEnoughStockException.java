package orderservice.exceptions;

public class NotEnoughStockException extends RuntimeException {
    public NotEnoughStockException(String productSKU) {
        super("Not enough stock available for product: " + productSKU);
    }
}
