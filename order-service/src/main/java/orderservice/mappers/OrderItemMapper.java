package orderservice.mappers;

import orderservice.dto.OrderItemDto;
import orderservice.entities.OrderItem;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderItemMapper {
    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemDto orderItemDto);

    @Mapping(source = "order.id", target = "orderId")
    OrderItemDto toDto(OrderItem orderItem);


    List<OrderItemDto> toDto(Collection<OrderItem> orderItem);
}