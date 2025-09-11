package userservice.mappers;

import org.mapstruct.*;
import userservice.dto.UserProfileDto;
import userservice.entities.UserProfile;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserProfileMapper {
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "address", ignore = true)
    UserProfile toEntity(UserProfileDto userProfileDto);

    @Mapping(source = "address.id", target = "addressId")
    @Mapping(source = "user.id", target = "userId")
    UserProfileDto toDto(UserProfile userProfile);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "address", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserProfile partialUpdate(UserProfileDto userProfileDto, @MappingTarget UserProfile userProfile);
}