package inventoryservice;

import inventoryservice.inbox.InboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@EnableConfigurationProperties({InboxProperties.class})
@SpringBootApplication
public class InventoryServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
        log.info("Inventory Service Application Started");

    }

}
