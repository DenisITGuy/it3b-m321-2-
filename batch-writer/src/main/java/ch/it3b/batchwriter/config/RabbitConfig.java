package ch.it3b.batchwriter.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer RabbitMQ.
 * Deklariert die Queues, aktiviert JSON und Batch-Empfang.
 */
@Configuration
public class RabbitConfig {

    /** Name der Queue, aus der Nachrichten gelesen werden. */
    public static final String QUEUE_NAME = "chat.persist";

    /** Name der Dead Letter Queue laut PLANUNG.md. */
    public static final String DLQ_NAME = "chat.dlq";

    /**
     * Deklariert die Queue chat.persist (durable).
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatPersistQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Deklariert die Queue chat.dlq (durable).
     * Existiert, damit das Pruefskript sie inspectieren kann (S5).
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatDlqQueue() {
        return new Queue(DLQ_NAME, true);
    }

    /**
     * Erstellt den JSON-MessageConverter.
     * Liest Nachrichten mit Header content_type: application/json (S5).
     *
     * @return Der MessageConverter
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Konfiguriert den Listener fuer Batch-Empfang (S4).
     *
     * @param connectionFactory Die RabbitMQ-Verbindung
     * @param batchSize Nachrichten pro Batch aus application.yml
     * @return Die konfigurierte Factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${app.batch-size:100}") int batchSize) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(batchSize);
        return factory;
    }
}