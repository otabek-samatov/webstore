package orderservice.mappers;

import orderservice.dto.OrderItemDto;
import orderservice.entities.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

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