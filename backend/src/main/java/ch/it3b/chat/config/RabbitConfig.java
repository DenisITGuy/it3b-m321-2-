package ch.it3b.chat.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer RabbitMQ im chat-service.
 * Deklariert die Queue 'chat.persist', an die der batch-writer lauscht.
 */
@Configuration
public class RabbitConfig {

    /** Name der Queue fuer die Persistenz (Bewertung 1) */
    public static final String QUEUE_NAME = "chat.persist";

    /**
     * Deklariert die Queue 'chat.persist'.
     * Stellt sicher, dass die Queue existiert, bevor Nachrichten gesendet werden.
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatPersistQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Erstellt den JSON-MessageConverter.
     * Wandelt Java-Objekte automatisch in JSON fuer RabbitMQ um.
     *
     * @return Der MessageConverter
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}