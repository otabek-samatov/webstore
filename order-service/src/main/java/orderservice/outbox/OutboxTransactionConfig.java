package orderservice.outbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class OutboxTransactionConfig {

    /**
     * Short, independent transaction for claiming an outbox event.
     * Commits immediately so the row lock from the claim UPDATE
     * is released before the Kafka send begins.
     */
    @Bean
    public TransactionTemplate outboxClaimTxTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}