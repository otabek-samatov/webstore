package userservice.mappers;

import org.mapstruct.*;
import userservice.dto.UserProfileDto;
import userservice.entities.UserProfile;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserProfileMapper {
    @Mapping(target = "user", ignore = true)
    UserProfile toEntity(UserProfileDto userProfileDto);

    @Mapping(source = "user.userName", target = "userName")
    UserProfileDto toDto(UserProfile userProfile);

    @Mapping(target = "user", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserProfile partialUpdate(UserProfileDto userProfileDto, @MappingTarget UserProfile userProfile);
}