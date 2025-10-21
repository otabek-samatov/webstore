package paymentservice.mappers;

import org.mapstruct.*;
import paymentservice.dto.RefundDto;
import paymentservice.entities.Refund;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RefundMapper {
    @Mapping(target = "payment", ignore = true)
    Refund toEntity(RefundDto refundDto);

    RefundDto toDto(Refund refund);

    @Mapping(target = "payment", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Refund partialUpdate(RefundDto refundDto, @MappingTarget Refund refund);
}