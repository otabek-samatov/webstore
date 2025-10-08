package orderservice.mappers;

import orderservice.dto.OrderDto;
import orderservice.entities.Order;
import org.mapstruct.*;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {
    @Mapping(target = "orderItems", ignore = true)
    Order toEntity(OrderDto orderDto);

    OrderDto toDto(Order order);

    @Mapping(target = "orderItems", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Order partialUpdate(OrderDto orderDto, @MappingTarget Order order);


    List<OrderDto> toDto(List<Order> order);
}