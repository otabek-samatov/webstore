package inventoryservice.mappers;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import org.mapstruct.*;

import java.util.Collection;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryMapper {

    InventoryDto toDto(Inventory inventory);

    Collection<InventoryDto> toDto(Collection<Inventory> inventory);

    Inventory toEntity(InventoryDto inventoryDto);

    Collection<Inventory> toEntity(Collection<InventoryDto> inventoryDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)Inventory partialUpdate(InventoryDto inventoryDto, @MappingTarget Inventory inventory);
}