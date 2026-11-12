package ch.it3b.chat.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer RabbitMQ im chat-service.
 * Deklariert die Persistenz-Queue und den Fanout-Exchange fuer die Live-Zustellung
 * und legt fest, dass Nachrichten als JSON auf die Leitung gehen.
 */
@Configuration
public class RabbitConfig {

    /** Name der Queue fuer die Persistenz (Bewertung 1). */
    public static final String QUEUE_NAME = "chat.persist";

    /** Name des Fanout-Exchange fuer die Live-Zustellung (Bewertung 2). */
    public static final String LIVE_EXCHANGE_NAME = "chat.messages";

    /**
     * Deklariert die Queue chat.persist, unveraendert aus Bewertung 1.
     * Sie bleibt durable und ohne Argumente, weil der batch-writer sie identisch
     * deklariert; abweichende Argumente lehnt RabbitMQ mit PRECONDITION_FAILED ab.
     *
     * @return Die Queue-Instanz
     */
    @Bean
    public Queue chatPersistQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Deklariert den Fanout-Exchange chat.messages.
     * Fanout legt jede Nachricht als Kopie in jede gebundene Queue. Jede Instanz
     * braucht dieselbe Kopie, deshalb gibt es hier nichts ueber Routing-Keys zu
     * steuern. Durable, damit der Exchange einen Neustart des Brokers ueberlebt.
     *
     * @return Der Exchange fuer die Live-Zustellung
     */
    @Bean
    public FanoutExchange chatMessagesExchange() {
        return new FanoutExchange(LIVE_EXCHANGE_NAME, true, false);
    }

    /**
     * Wandelt Java-Objekte in JSON fuer RabbitMQ um.
     * Dieselbe Instanz erzeugt spaeter auch das JSON fuer data: am Stream,
     * damit Live-Ereignis und nachgeholtes Ereignis dasselbe Format haben.
     *
     * @return Der JSON-Converter
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Legt den RabbitTemplate ausdruecklich mit dem JSON-Converter an.
     * Ohne diese Zuordnung koennte der Standard-Serializer greifen und das Objekt
     * binaer senden; der Vertrag verlangt JSON mit content_type application/json.
     *
     * @param connectionFactory Die Verbindung zum Broker
     * @param messageConverter Der JSON-Converter von oben
     * @return Der RabbitTemplate fuer beide Versandwege
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}