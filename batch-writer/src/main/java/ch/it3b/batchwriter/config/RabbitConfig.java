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
     * Deklariert die Queue chat.persist (durable), exakt wie der chat-service.
     * Die Argumente muessen identisch sein: RabbitMQ lehnt eine Deklaration mit
     * abweichenden Argumenten mit PRECONDITION_FAILED (406) ab und schliesst den
     * Channel. Dadurch wuerde der Publish des chat-service mit HTTP 500 scheitern
     * und Nachrichten gingen verloren (S3).
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatPersistQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Deklariert die Queue chat.dlq (durable) gemaess PLANUNG.md.
     * Sie bleibt leer, weil Duplikate regulaer verarbeitet und geackt werden (S5).
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatDlqQueue() {
        return new Queue(DLQ_NAME, true);
    }

    /**
     * Erstellt den JSON-MessageConverter.
     * Liest Nachrichten mit dem Header content_type: application/json (S5).
     *
     * @return Der MessageConverter
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Konfiguriert den Listener fuer Batch-Empfang (S4).
     * consumerBatchEnabled sammelt einzelne Nachrichten zu einer Liste, damit pro
     * Batch genau eine Transaktion entsteht. setBatchListener(true) wird bewusst
     * nicht gesetzt: das wuerde ein JSON-Array als Payload erwarten, der
     * chat-service sendet aber einzelne JSON-Objekte.
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
        factory.setConsumerBatchEnabled(true);

        // Ungueltige Batch-Groesse faengt der Dienst ab, statt beim Start zu scheitern
        if (batchSize > 0) {
            factory.setBatchSize(batchSize);
        } else {
            factory.setConsumerBatchEnabled(false);
        }

        return factory;
    }
}