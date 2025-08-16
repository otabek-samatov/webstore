package inventoryservice.mappers;

import inventoryservice.dto.InventoryChangeDto;
import inventoryservice.entities.InventoryChange;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.Collection;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryChangeMapper {

    @Mapping(source = "inventory.id", target = "inventoryId")
    InventoryChangeDto toDto(InventoryChange inventoryChange);

    Collection<InventoryChangeDto> toDto(Collection<InventoryChange> inventoryChange);
}