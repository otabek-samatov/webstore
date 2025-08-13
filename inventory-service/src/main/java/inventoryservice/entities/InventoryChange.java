package inventoryservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "inventory_change")
public class InventoryChange {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_change_seq")
    @SequenceGenerator(name = "inventory_change_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "change_amount", nullable = false)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private ReasonType eventType;

    @Column(name = "event_id", nullable = false)
    private Long eventID;

}