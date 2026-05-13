package orderservice.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum OrderStatus {

    REFUNDED,
    CANCELLED,
    COMPLETED(REFUNDED),
    NEW(COMPLETED, CANCELLED);

    private final List<OrderStatus> nextPossibleStatuses;

    OrderStatus(OrderStatus... orderStatuses) {
        if (orderStatuses == null) {
            nextPossibleStatuses = new ArrayList<>();
        } else {
            nextPossibleStatuses = Arrays.asList(orderStatuses);
        }
    }

    public boolean isAcceptableNextStatus(OrderStatus nextStatus) {
        return nextPossibleStatuses.contains(nextStatus);
    }

}

