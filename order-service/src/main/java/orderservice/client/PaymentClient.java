package orderservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.PaymentDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.exceptions.PaymentFailedException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Thin client over payment-service's {@code POST /v1/payments}. Shared by the
 * create-order saga ({@code ProcessPaymentStep}) and the payment-retry path
 * ({@code OrderManager.retryPayment}).
 * <p>
 * Maps transport failures to domain exceptions (4xx → {@link PaymentFailedException},
 * 5xx → {@link IllegalStateException}). A <em>declined</em> payment is not a
 * transport failure: payment-service returns 200 with {@code paymentStatus=FAILED},
 * and the returned {@link PaymentDto} is handed back to the caller to act on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentClient {

    public static final String STATUS_COMPLETED = "COMPLETED";

    private final RestClient restClient;

    /**
     * Charges the customer for the given order. The amount is computed from the
     * order's items, shipping, and tax — so the order's items must be initialised
     * before calling (the create flow passes an in-memory order; the retry flow
     * fetches one with items).
     *
     * @return the payment-service response ({@code paymentStatus} is
     * {@code COMPLETED} or {@code FAILED})
     */
    public PaymentDto charge(Order order) {
        PaymentDto request = new PaymentDto();
        request.setOrderId(order.getId());
        request.setUserId(order.getCustomerId());
        request.setAmount(computeTotal(order));

        PaymentDto response = restClient.post()
                .uri("http://payment-service/v1/payments")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("Payment rejected status={} body={}", res.getStatusCode(), body);
                    throw new PaymentFailedException("payment-service rejected payment: " + body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("payment-service 5xx for status={}", res.getStatusCode());
                    throw new IllegalStateException("payment-service failed: " + res.getStatusCode());
                })
                .body(PaymentDto.class);

        if (response == null) {
            throw new IllegalStateException("payment-service returned an empty body for orderId=" + order.getId());
        }

        log.info("Payment attempt orderId={} amount={} status={} paymentId={}",
                order.getId(), request.getAmount(), response.getPaymentStatus(), response.getId());
        return response;
    }

    public boolean isCompleted(PaymentDto response) {
        return response != null && STATUS_COMPLETED.equalsIgnoreCase(response.getPaymentStatus());
    }

    private BigDecimal computeTotal(Order order) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            long quantity = item.getQuantity() == null ? 0L : item.getQuantity();
            itemsTotal = itemsTotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        return itemsTotal
                .add(order.getShippingCost() == null ? BigDecimal.ZERO : order.getShippingCost())
                .add(order.getTaxAmount() == null ? BigDecimal.ZERO : order.getTaxAmount());
    }
}
