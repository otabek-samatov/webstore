package productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import productservice.inbox.InboxProperties;
import productservice.outbox.OutboxProperties;

@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, InboxProperties.class})
@SpringBootApplication
public class ProductServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductServiceApplication.class, args);
	}

}
