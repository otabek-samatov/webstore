package orderservice.saga.createorder;

import lombok.extern.slf4j.Slf4j;
import orderservice.client.PaymentClient;
import orderservice.dto.CreateOrderDto;
import orderservice.entities.Order;
import orderservice.mappers.AddressMapper;
import orderservice.mappers.OrderItemMapper;
import orderservice.outbox.OutboxPublisher;
import orderservice.repositories.OrderRepository;
import orderservice.saga.SagaContext;
import orderservice.saga.SagaOrchestrator;
import orderservice.saga.SagaStep;
import orderservice.validators.BaseValidator;
import orderservice.validators.OrderItemValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Orchestration-based saga that drives the order-creation workflow:
 * <ol>
 *   <li>{@link PriceItemsStep} — lookup unit prices from inventory-service</li>
 *   <li>{@link ReserveStockStep} — reserve stock; compensation publishes a
 *       {@code release} stock-status event via the outbox</li>
 *   <li>{@link PersistOrderStep} — build and save the {@link Order};
 *       compensation cancels the order ({@code NEW → CANCELLED})</li>
 *   <li>{@link ProcessPaymentStep} — charge the customer via payment-service.
 *       A <em>declined</em> payment moves the order to {@code PAYMENT_FAILED}
 *       and keeps the stock (retry-able, no compensation); a payment-service
 *       transport error (4xx/5xx) throws and triggers the compensations above</li>
 * </ol>
 * The orchestrator records the saga's lifecycle in {@code saga_instance};
 * if any step throws, previously-executed steps are compensated in
 * reverse order and the saga ends in {@code COMPENSATED}.
 */
@Slf4j
@Component
public class CreateOrderSaga {

    public static final String SAGA_TYPE = "create-order";

    static final String CTX_ORDER_DTO = "orderDto";
    static final String CTX_PRICES = "prices";
    static final String CTX_RESERVED_ITEMS = "reservedItems";
    static final String CTX_ORDER = "order";
    static final String CTX_PAYMENT_ID = "paymentId";

    private final SagaOrchestrator orchestrator;
    private final BaseValidator baseValidator;
    private final OrderItemValidator orderItemValidator;
    private final RestClient restClient;
    private final OutboxPublisher outboxPublisher;
    private final OrderRepository orderRepository;
    private final OrderItemMapper orderItemMapper;
    private final AddressMapper addressMapper;
    private final PaymentClient paymentClient;
    private final TransactionTemplate transactionTemplate;
    private final String stockStatusTopic;

    public CreateOrderSaga(SagaOrchestrator orchestrator,
                           BaseValidator baseValidator,
                           OrderItemValidator orderItemValidator,
                           RestClient restClient,
                           OutboxPublisher outboxPublisher,
                           OrderRepository orderRepository,
                           OrderItemMapper orderItemMapper,
                           AddressMapper addressMapper,
                           PaymentClient paymentClient,
                           PlatformTransactionManager transactionManager,
                           @Value("${topic.stock.status}") String stockStatusTopic) {
        this.orchestrator = orchestrator;
        this.baseValidator = baseValidator;
        this.orderItemValidator = orderItemValidator;
        this.restClient = restClient;
        this.outboxPublisher = outboxPublisher;
        this.orderRepository = orderRepository;
        this.orderItemMapper = orderItemMapper;
        this.addressMapper = addressMapper;
        this.paymentClient = paymentClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stockStatusTopic = stockStatusTopic;
    }

    public Order execute(CreateOrderDto orderDto) {
        if (orderDto == null) {
            throw new IllegalArgumentException("orderDto is null");
        }

        baseValidator.validate(orderDto);
        orderItemValidator.validate(orderDto.getOrderItems());

        log.info("Starting create-order saga customerId={} itemCount={}",
                orderDto.getCustomerId(),
                orderDto.getOrderItems() == null ? 0 : orderDto.getOrderItems().size());

        SagaContext context = new SagaContext();
        context.put(CTX_ORDER_DTO, orderDto);

        List<SagaStep> steps = List.of(
                new PriceItemsStep(restClient),
                new ReserveStockStep(restClient, outboxPublisher, transactionTemplate, stockStatusTopic),
                new PersistOrderStep(orderRepository, orderItemMapper, addressMapper, transactionTemplate),
                new ProcessPaymentStep(paymentClient, orderRepository, transactionTemplate)
        );

        orchestrator.execute(SAGA_TYPE, steps, context);

        Order order = context.get(CTX_ORDER, Order.class);
        log.info("Create-order saga finished orderId={} customerId={}",
                order == null ? null : order.getId(), orderDto.getCustomerId());
        return order;
    }
}
