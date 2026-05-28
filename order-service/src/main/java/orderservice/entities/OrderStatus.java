package orderservice.entities;

import java.util.List;

public enum OrderStatus {

    REFUNDED,
    CANCELLED,
    COMPLETED(REFUNDED),
    PAYMENT_FAILED(COMPLETED, CANCELLED),
    NEW(COMPLETED, CANCELLED, PAYMENT_FAILED);

    private final List<OrderStatus> nextPossibleStatuses;

    OrderStatus(OrderStatus... orderStatuses) {
        this.nextPossibleStatuses = List.of(orderStatuses);
    }

    public boolean isAcceptableNextStatus(OrderStatus nextStatus) {
        return nextPossibleStatuses.contains(nextStatus);
    }

}

