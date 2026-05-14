package orderservice.entities;

import java.util.List;

public enum OrderStatus {

    REFUNDED,
    CANCELLED,
    COMPLETED(REFUNDED),
    NEW(COMPLETED, CANCELLED);

    private final List<OrderStatus> nextPossibleStatuses;

    OrderStatus(OrderStatus... orderStatuses) {
        this.nextPossibleStatuses = List.of(orderStatuses);
    }

    public boolean isAcceptableNextStatus(OrderStatus nextStatus) {
        return nextPossibleStatuses.contains(nextStatus);
    }

}

