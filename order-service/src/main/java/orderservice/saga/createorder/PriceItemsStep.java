package orderservice.saga.createorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.InventoryDto;
import orderservice.dto.OrderItemDto;
import orderservice.saga.SagaContext;
import orderservice.saga.SagaStep;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Saga step 1: looks up unit prices for each requested SKU from
 * inventory-service. Read-only — no compensation needed.
 */
@Slf4j
@RequiredArgsConstructor
class PriceItemsStep implements SagaStep {

    private final RestClient restClient;

    @Override
    public String name() {
        return "price-items";
    }

    @Override
    public void execute(SagaContext context) {
        CreateOrderDto orderDto = context.get(CreateOrderSaga.CTX_ORDER_DTO, CreateOrderDto.class);
        List<OrderItemDto> items = orderDto.getOrderItems();

        Map<String, BigDecimal> priceMap = fetchPrices(items);
        context.put(CreateOrderSaga.CTX_PRICES, priceMap);

        log.info("Saga step price-items resolved {} sku prices sagaId={}",
                priceMap.size(), context.getSagaId());
    }

    private Map<String, BigDecimal> fetchPrices(List<OrderItemDto> dtos) {
        if (CollectionUtils.isEmpty(dtos)) {
            return Collections.emptyMap();
        }

        List<String> skus = new ArrayList<>(dtos.size());
        for (OrderItemDto dto : dtos) {
            skus.add(dto.getProductSKU());
        }

        List<InventoryDto> prices = restClient.post()
                .uri("http://inventory-service/v1/inventory/prices")
                .body(skus)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("Price lookup rejected status={} body={}", res.getStatusCode(), body);
                    throw new IllegalArgumentException("Inventory rejected price request: " + body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("inventory-service 5xx for prices status={}", res.getStatusCode());
                    throw new IllegalStateException("inventory-service price lookup failed: " + res.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {
                });

        if (CollectionUtils.isEmpty(prices)) {
            return Collections.emptyMap();
        }

        return prices.stream()
                .collect(Collectors.toMap(InventoryDto::getProductSKU, InventoryDto::getSellPrice));
    }
}
