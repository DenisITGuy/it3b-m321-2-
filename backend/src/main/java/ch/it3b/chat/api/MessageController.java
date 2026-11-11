package ch.it3b.chat.api;

import ch.it3b.chat.config.RabbitConfig;
import ch.it3b.chat.messaging.MessageEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST-Endpunkte des chat-service.
 * POST nimmt Nachrichten an und legt sie in die Queue chat.persist.
 * Kein Auth-Token noetig: Keycloak ist laut Bewertungsauftrag nicht Teil dieser Aufgabe.
 */

// @RestController = Diese Klasse ist ein REST-Controller (verarbeitet HTTP-Anfragen, gibt JSON/Text zurück)
// @RequestMapping = Basis-Pfad für alle Endpunkte in dieser Klasse
// Hier funktionieren beide Pfade: /messages UND /api/messages
@RestController
@RequestMapping({"/messages", "/api/messages"})
public class MessageController {

    // RabbitTemplate = Spring Boot Klasse zum Senden von Nachrichten an RabbitMQ (Message Queue)
    // final = Wird nur einmal gesetzt (über den Konstruktor)
    private final RabbitTemplate rabbitTemplate;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param rabbitTemplate Sendet Nachrichten an RabbitMQ
     */
    
    // Konstruktor für Dependency Injection
    // Spring Boot erstellt automatisch ein RabbitTemplate und gibt es hier rein
    // Dependency Injection = Abhängigkeiten werden von außen gegeben, nicht selbst erstellt
    public MessageController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Nimmt eine Nachricht an, vergibt eine eindeutige ID (wichtig fuer S5)
     * und legt sie in die Queue chat.persist.
     *
     * @param request Die Nachricht vom Client
     * @return Das Event inklusive generierter ID als Bestaetigung
     */
    
    // @PostMapping = Diese Methode reagiert auf HTTP POST-Anfragen (für Daten erstellen/senden)
    // @ResponseStatus(HttpStatus.ACCEPTED) = Gibt HTTP 202 zurück ("akzeptiert, wird asynchron verarbeitet")
    // @RequestBody = Der JSON-Body der Anfrage wird automatisch in SendMessageRequest umgewandelt
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageEvent sendMessage(@RequestBody SendMessageRequest request) {
        // Ternary Operator: Bedingung ? wenn wahr : wenn falsch
        // Wenn sender vorhanden UND nicht leer → nimm sender, sonst "anonymous"
        // .isBlank() prüft ob String nur Leerzeichen oder leer ist
        String sender = (request.sender() != null && !request.sender().isBlank()) 
            ? request.sender() 
            : "anonymous";
        
        // Erstelle ein neues MessageEvent mit allen Daten:
        // - UUID.randomUUID() = Generiert eine weltweit eindeutige ID (z.B. 550e8400-e29b-11d4...)
        // - request.roomId() = Raum-ID aus dem Request
        // - sender = Absender (entweder aus Request oder "anonymous")
        // - request.content() = Nachrichteninhalt
        // - Instant.now() = Aktueller Zeitstempel in UTC (sehr präzise)
        MessageEvent event = new MessageEvent(
                UUID.randomUUID(),
                request.roomId(),
                sender,
                request.content(),
                Instant.now());
        
        // Schickt das Event an die RabbitMQ Queue "chat.persist"
        // convertAndSend = Konvertiert das Objekt zu JSON und sendet es asynchron
        // Ein anderer Service (persist-service) holt es später ab und speichert es in der DB
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_NAME, event);
        
        // Gibt das Event zurück als Bestätigung an den Client
        // Der Client sieht die generierte ID und weiß: Nachricht wurde angenommen
        return event;
    }
}