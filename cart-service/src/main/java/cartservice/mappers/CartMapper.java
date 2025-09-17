package cartservice.mappers;

import cartservice.dto.CartDto;
import cartservice.entities.Cart;
import org.mapstruct.*;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CartMapper {
    @Mapping(target = "cartItems", ignore = true)
    Cart toEntity(CartDto cartDto);

    CartDto toDto(Cart cart);

    @Mapping(target = "cartItems", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Cart partialUpdate(CartDto cartDto, @MappingTarget Cart cart);

    List<CartDto> toDto(List<Cart> cart);
}