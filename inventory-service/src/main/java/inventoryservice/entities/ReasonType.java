package inventoryservice.entities;

public enum ReasonType {
    RESERVE_STOCK,
    COMMIT_STOCK,
    REVERT_STOCK,
    INCREASED_BY_WAREHOUSE,
    CANCELLED_BY_WAREHOUSE,
}
