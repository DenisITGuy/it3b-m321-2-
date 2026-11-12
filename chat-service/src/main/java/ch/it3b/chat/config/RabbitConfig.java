package ch.it3b.chat.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer RabbitMQ im chat-service.
 * Deklariert die Persistenz-Queue, den Fanout-Exchange und die eigene Live-Queue
 * dieser Instanz samt Binding und legt fest, dass JSON auf die Leitung geht.
 */
@Configuration
public class RabbitConfig {

    /** Name der Queue fuer die Persistenz (Bewertung 1). */
    public static final String QUEUE_NAME = "chat.persist";

    /** Name des Fanout-Exchange fuer die Live-Zustellung (Bewertung 2). */
    public static final String LIVE_EXCHANGE_NAME = "chat.messages";

    /**
     * Deklariert die Queue chat.persist, unveraendert aus Bewertung 1.
     * Durable und ohne Argumente, weil der batch-writer sie identisch deklariert;
     * abweichende Argumente lehnt RabbitMQ mit PRECONDITION_FAILED ab.
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
     * Deklariert die Live-Queue dieser Instanz.
     * Nicht durable: Live-Ereignisse sollen einen Broker-Neustart nicht ueberleben,
     * die Vollstaendigkeit liegt in der Datenbank. Exclusive bindet die Queue an
     * diese Verbindung, autoDelete entfernt sie, wenn der letzte Consumer weg ist.
     * Beendet docker compose den Container, schliesst der Socket und der Broker
     * loescht die Queue sofort. Beim Pausieren bleibt die Verbindung offen, die
     * Queue besteht weiter.
     *
     * @param liveQueueName Name aus live.queue.name, pro Instanz eindeutig
     * @return Die Queue-Instanz dieser Instanz
     */
    @Bean
    public Queue liveQueue(@Value("${live.queue.name}") String liveQueueName) {
        return new Queue(liveQueueName, false, true, true);
    }

    /**
     * Bindet die Live-Queue an den Fanout-Exchange.
     * Ein Routing-Key wird nicht gebraucht, Fanout ignoriert ihn.
     *
     * @param liveQueue Die Queue dieser Instanz
     * @param chatMessagesExchange Der Fanout-Exchange
     * @return Das Binding
     */
    @Bean
    public Binding liveBinding(@Qualifier("liveQueue") Queue liveQueue,
                               FanoutExchange chatMessagesExchange) {
        return BindingBuilder.bind(liveQueue).to(chatMessagesExchange);
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

    /**
     * Baut die Listener-Factory fuer die Live-Queue.
     * Genau ein Consumer, damit die Reihenfolge eines Absenders erhalten bleibt
     * und S4 pro Queue einen Consumer zaehlt. Prefetch begrenzt die Nachrichten
     * in flight. missingQueuesFatal false haelt den Consumer am Leben, wenn die
     * Queue nach einem Broker-Neustart kurz fehlt und neu deklariert wird.
     * Fehler werden nicht requeued: die Live-Zustellung ist Best-Effort, ein
     * erneutes Zustellen wuerde bei den anderen Clients Duplikate erzeugen.
     *
     * @param connectionFactory Die Verbindung zum Broker
     * @param messageConverter Der JSON-Converter
     * @param prefetch Nachrichten in flight, aus live.prefetch
     * @return Die konfigurierte Factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory liveListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter,
            @Value("${live.prefetch:10}") int prefetch) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(prefetch);
        factory.setMissingQueuesFatal(false);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}