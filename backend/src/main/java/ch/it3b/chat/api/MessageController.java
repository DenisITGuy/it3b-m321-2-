package ch.it3b.chat.api;

import ch.it3b.chat.config.RabbitConfig;
import ch.it3b.chat.messaging.MessageEvent;
import ch.it3b.chat.model.ChatMessage;
import ch.it3b.chat.model.MessageRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
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
    private final MessageRepository messageRepository;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param rabbitTemplate Sendet Nachrichten an RabbitMQ
     * @param messageRepository Liest den Verlauf aus der Datenbank
     */
    public MessageController(RabbitTemplate rabbitTemplate, MessageRepository messageRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageRepository = messageRepository;
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
        String sender = request.sender() != null ? request.sender() : "anonymous";
        MessageEvent event = new MessageEvent(
                UUID.randomUUID(),
                request.roomId(),
                sender,
                request.content(),
                Instant.now());
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_NAME, event);
        return event;
    }

    /**
     * Liest den Verlauf eines Raums aus der Datenbank.
     *
     * @param roomId Die ID des Raums
     * @return Liste der Nachrichten, aelteste zuerst
     */
    @GetMapping
    public List<ChatMessage> getMessages(@RequestParam UUID roomId) {
        return messageRepository.findAllByRoomIdOrderByCreatedAtAsc(roomId);
    }
}