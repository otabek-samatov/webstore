package userservice.mappers;

import org.mapstruct.*;
import userservice.dto.UserDto;
import userservice.entities.User;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(source = "securityRoleId", target = "securityRole.id")
    User toEntity(UserDto userDto);

    @Mapping(source = "securityRole.id", target = "securityRoleId")
    UserDto toDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    User partialUpdate(UserDto userDto, @MappingTarget User user);
}