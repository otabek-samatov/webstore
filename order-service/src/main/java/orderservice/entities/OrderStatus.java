package orderservice.entities;

public enum OrderStatus {
   
PENDING,           // Order created, awaiting payment
PROCESSING,        // Order being prepared for shipment
SHIPPED,          // Order dispatched to customer
DELIVERED,        // Order delivered successfully
CANCELLED
}

