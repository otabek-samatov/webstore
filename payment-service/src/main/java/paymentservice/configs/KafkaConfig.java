package paymentservice.configs;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import paymentservice.dto.kafka.OrderStatusKafka;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${bootstrap.servers}")
    private String bootstrapServers;

    @Value("${topic.order.status}")
    private String orderStatusTopic;

    @Value("${num.partitions}")
    private int partitions;

    @Value("${replication.factor}")
    private short replicationFactor;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private int serverPort;


    @Bean
    public NewTopic stockStatusTopic() {
        return new NewTopic(orderStatusTopic, partitions, replicationFactor);
    }

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // ============================================
        // EXACTLY-ONCE SEMANTICS CONFIGURATION
        // ============================================

        // Transactional ID - REQUIRED for exactly-once semantics
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, applicationName + "-tx-" + serverPort);

        // Enable idempotent producer
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // All replicas must acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retry configuration
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        // Max in-flight requests
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return props;
    }

    @Bean
    public ProducerFactory<String, OrderStatusKafka> producerFactory() {
        DefaultKafkaProducerFactory<String, OrderStatusKafka> factory =
                new DefaultKafkaProducerFactory<>(producerConfigs());
        return factory;
    }

    @Bean
    public KafkaTemplate<String, OrderStatusKafka> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaTransactionManager<String, OrderStatusKafka> kafkaTransactionManager(
            ProducerFactory<String, OrderStatusKafka> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    private Map<String, Object> consumerConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-group");
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return configs;
    }

    @Bean
    public ConsumerFactory<String, OrderStatusKafka> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(),
                new StringDeserializer(),
                new JsonDeserializer<>(OrderStatusKafka.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderStatusKafka> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderStatusKafka> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(partitions);

        // Modern approach: Use RECORD acknowledgment mode
        // When using @Transactional, Spring Kafka will automatically handle
        // offset commits within the transaction boundary
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }
}