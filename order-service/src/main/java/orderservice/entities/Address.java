package orderservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class Address {

    @NotBlank(message = "Country should be specified")
    @Column(name = "country", nullable = false)
    private String country;

    @NotBlank(message = "Region should be specified")
    @Column(name = "region", nullable = false)
    private String region;

    @NotBlank(message = "City should be specified")
    @Column(name = "city", nullable = false)
    private String city;

    @NotBlank(message = "Street should be specified")
    @Column(name = "street", nullable = false)
    private String street;

    @NotBlank(message = "AddressLine should be specified")
    @Column(name = "address_line", nullable = false)
    private String addressLine;


}