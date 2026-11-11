package ch.it3b.chat.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer RabbitMQ im chat-service.
 * Deklariert die Queue 'chat.persist', an die der batch-writer lauscht.
 */

// @Configuration = Markiert diese Klasse als Spring-Konfigurationsklasse
// Spring Boot scannt diese Klasse beim Start und führt die @Bean-Methoden aus
@Configuration
public class RabbitConfig {

    /** Name der Queue fuer die Persistenz (Bewertung 1) */
    // public static final = Konstante, die von überall gelesen werden kann, aber nicht verändert wird
    // Wird im MessageController benutzt, um an genau diese Queue zu senden
    public static final String QUEUE_NAME = "chat.persist";

    /**
     * Deklariert die Queue 'chat.persist'.
     * Stellt sicher, dass die Queue existiert, bevor Nachrichten gesendet werden.
     *
     * @return Die Queue-Instanz
     */
    
    // @Bean = Sagt Spring Boot: "Führe diese Methode aus und speichere das Ergebnis 
    // als Objekt (Bean) im Spring Container, damit andere Teile der App es benutzen können"
    @Bean
    public Queue chatPersistQueue() {
        // new Queue(name, durable)
        // name = "chat.persist" (der Name der Queue in RabbitMQ)
        // true = "durable" bedeutet: Die Queue überlebt einen Neustart von RabbitMQ
        // (Wichtig, damit keine Nachrichten verloren gehen wenn der Server neu startet)
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Erstellt den JSON-MessageConverter.
     * Wandelt Java-Objekte automatisch in JSON fuer RabbitMQ um.
     *
     * @return Der MessageConverter
     */
    
    // Zweites @Bean: Registriert den JSON-Konverter im Spring Container
    // Spring Boot benutzt diesen Konverter dann automatisch für das RabbitTemplate
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        // Jackson2JsonMessageConverter = Nutzt die Jackson-Bibliothek (Standard für JSON in Java)
        // Damit werden unsere Java-Objekte (wie MessageEvent) automatisch in JSON umgewandelt,
        // bevor sie in die RabbitMQ Queue geschickt werden.
        // Der Empfänger (z.B. persist-service) bekommt dann JSON und kann es wieder in Objekte umwandeln.
        return new Jackson2JsonMessageConverter();
    }
}