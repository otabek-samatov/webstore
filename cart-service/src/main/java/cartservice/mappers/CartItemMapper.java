package cartservice.mappers;

import cartservice.dto.CartItemDto;
import cartservice.entities.CartItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CartItemMapper {
    @Mapping(target = "cart", ignore = true)
    CartItem toEntity(CartItemDto cartItemDto);

    @Mapping(source = "cart.id", target = "cartId")
    CartItemDto toDto(CartItem cartItem);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "cart", ignore = true)
    CartItem partialUpdate(CartItemDto cartItemDto, @MappingTarget CartItem cartItem);


    List<CartItemDto> toDto(List<CartItem> cartItem);
}