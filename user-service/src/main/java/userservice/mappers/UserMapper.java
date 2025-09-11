package userservice.mappers;

import org.mapstruct.*;
import userservice.dto.UserDto;
import userservice.entities.User;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(target = "securityRole", ignore = true)
    User toEntity(UserDto userDto);

    @Mapping(source = "securityRole.id", target = "securityRoleId")
    UserDto toDto(User user);

    @Mapping(target = "securityRole", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    User partialUpdate(UserDto userDto, @MappingTarget User user);
}