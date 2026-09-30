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
@RestController
@RequestMapping({"/messages", "/api/messages"})
public class MessageController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param rabbitTemplate Sendet Nachrichten an RabbitMQ
     */
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
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageEvent sendMessage(@RequestBody SendMessageRequest request) {
        // sender: aus dem Request falls vorhanden, sonst Default
        String sender = (request.sender() != null && !request.sender().isBlank()) 
            ? request.sender() 
            : "anonymous";
        
        MessageEvent event = new MessageEvent(
                UUID.randomUUID(),
                request.roomId(),
                sender,
                request.content(),
                Instant.now());
        
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_NAME, event);
        return event;
    }
}