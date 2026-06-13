package inventoryservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "inventory_change")
@SequenceGenerator(name = "entity_seq", sequenceName = "inventory_change_seq", allocationSize = 50, initialValue = 1)
public class InventoryChange extends CoreEntity {

    @CreationTimestamp
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @NotNull
    @Column(name = "change_amount", nullable = false)
    private Long changeAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private ReasonType eventType;

    @NotNull
    @Column(name = "event_id", nullable = false)
    private Long eventID = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Inventory inventory;

}