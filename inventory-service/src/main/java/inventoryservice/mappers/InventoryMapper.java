package inventoryservice.mappers;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import inventoryservice.entities.InventoryChange;
import org.mapstruct.*;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryMapper {
    Inventory toEntity(InventoryDto inventoryDto);

    InventoryDto toDto(Inventory inventory);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Inventory partialUpdate(InventoryDto inventoryDto, @MappingTarget Inventory inventory);

    Collection<Inventory> toEntity(Collection<InventoryDto> inventoryDto);

    Collection<InventoryDto> toDto(Collection<Inventory> inventory);


    default Set<Long> changesToChangeIds(Set<InventoryChange> changes) {
        return changes != null ? changes.stream().map(InventoryChange::getId).collect(Collectors.toSet()) : null;
    }
}