package orderservice.saga.createorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.client.PaymentClient;
import orderservice.dto.PaymentDto;
import orderservice.entities.Order;
import orderservice.entities.OrderStatus;
import orderservice.repositories.OrderRepository;
import orderservice.saga.SagaContext;
import orderservice.saga.SagaStep;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga step 4 (final): charges the customer via payment-service for the
 * persisted order.
 * <p>
 * A <strong>declined</strong> payment (payment-service returns
 * {@code paymentStatus=FAILED}) is a business outcome, not a saga failure: the
 * order is moved to {@code PAYMENT_FAILED}, the reserved stock is <strong>kept</strong>
 * (so the customer can retry payment via {@code OrderManager.retryPayment}), and
 * the step returns normally — no compensation runs.
 * <p>
 * A transport failure (payment-service 4xx/5xx, surfaced by {@link PaymentClient}
 * as {@link orderservice.exceptions.PaymentFailedException} /
 * {@link IllegalStateException}) <em>is</em> a saga failure: it propagates, and
 * the orchestrator compensates the earlier steps (cancel order + release stock).
 * <p>
 * On a successful payment the order is left {@code NEW}; payment-service's async
 * {@code PaymentStatusMessage} event drives the {@code NEW → COMPLETED} transition.
 */
@Slf4j
@RequiredArgsConstructor
class ProcessPaymentStep implements SagaStep {

    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String name() {
        return "process-payment";
    }

    @Override
    public void execute(SagaContext context) {
        Order order = context.get(CreateOrderSaga.CTX_ORDER, Order.class);
        if (order == null || order.getId() == null) {
            throw new IllegalStateException("process-payment requires a persisted order");
        }

        PaymentDto response = paymentClient.charge(order);
        context.put(CreateOrderSaga.CTX_PAYMENT_ID, response.getId());

        if (paymentClient.isCompleted(response)) {
            log.info("Saga step process-payment completed orderId={} paymentId={} sagaId={}",
                    order.getId(), response.getId(), context.getSagaId());
            return;
        }

        // Declined: persist PAYMENT_FAILED, keep stock reserved, do NOT compensate.
        Long orderId = order.getId();
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(orderId).ifPresent(persisted -> {
                    persisted.setOrderStatus(OrderStatus.PAYMENT_FAILED);
                    orderRepository.save(persisted);
                }));
        // keep the in-memory order returned to the caller consistent with the DB
        order.setOrderStatus(OrderStatus.PAYMENT_FAILED);

        log.info("Saga step process-payment DECLINED orderId={} paymentId={} -> PAYMENT_FAILED (stock kept) sagaId={}",
                orderId, response.getId(), context.getSagaId());
    }
}
