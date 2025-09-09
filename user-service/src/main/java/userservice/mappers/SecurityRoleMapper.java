package userservice.mappers;

import org.mapstruct.*;
import userservice.dto.SecurityRoleDto;
import userservice.entities.SecurityRole;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SecurityRoleMapper {
    SecurityRole toEntity(SecurityRoleDto securityRoleDto);

    SecurityRoleDto toDto(SecurityRole securityRole);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    SecurityRole partialUpdate(SecurityRoleDto securityRoleDto, @MappingTarget SecurityRole securityRole);
}