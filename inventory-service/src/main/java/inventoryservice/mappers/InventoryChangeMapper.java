package inventoryservice.mappers;

import inventoryservice.dto.InventoryChangeDto;
import inventoryservice.entities.InventoryChange;
import org.mapstruct.*;

import java.util.Collection;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryChangeMapper {
    @Mapping(target = "inventory", ignore = true)
    InventoryChange toEntity(InventoryChangeDto inventoryChangeDto);

    @Mapping(source = "inventory.id", target = "inventoryId")
    InventoryChangeDto toDto(InventoryChange inventoryChange);

    @Mapping(target = "inventory", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    InventoryChange partialUpdate(InventoryChangeDto inventoryChangeDto, @MappingTarget InventoryChange inventoryChange);

    Collection<InventoryChange> toEntity(Collection<InventoryChangeDto> inventoryChangeDto);

    Collection<InventoryChangeDto> toDto(Collection<InventoryChange> inventoryChange);
}