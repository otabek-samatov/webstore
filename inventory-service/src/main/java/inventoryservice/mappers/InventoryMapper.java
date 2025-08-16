package inventoryservice.mappers;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.Collection;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryMapper {

    InventoryDto toDto(Inventory inventory);

    Collection<InventoryDto> toDto(Collection<Inventory> inventory);

}