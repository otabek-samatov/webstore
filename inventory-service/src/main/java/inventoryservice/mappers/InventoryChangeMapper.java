package inventoryservice.mappers;

import inventoryservice.dto.InventoryChangeDto;
import inventoryservice.entities.InventoryChange;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryChangeMapper {
    InventoryChange toEntity(InventoryChangeDto inventoryChangeDto);

    InventoryChangeDto toDto(InventoryChange inventoryChange);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    InventoryChange partialUpdate(InventoryChangeDto inventoryChangeDto, @MappingTarget InventoryChange inventoryChange);
}