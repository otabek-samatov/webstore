package orderservice.entities;

public enum OrderStatus {

    NEW,           // Order created, awaiting payment
    PROCESSING,        // Order being prepared for shipment
    DELIVERED,        // Order delivered successfully
    CANCELLED,
    REFUNDED
}

