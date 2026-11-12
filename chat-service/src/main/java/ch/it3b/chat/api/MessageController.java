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
 * POST nimmt eine Nachricht an und schickt sie auf zwei Wege: in die Queue
 * chat.persist fuer die Datenbank und an den Fanout-Exchange chat.messages
 * fuer die Live-Zustellung. Kein Auth-Token, Keycloak ist nicht Teil des Auftrags.
 */
@RestController
@RequestMapping({"/messages", "/api/messages"})
public class MessageController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Konstruktor fuer Dependency Injection.
     * Spring gibt hier den RabbitTemplate herein, der mit JSON serialisiert.
     *
     * @param rabbitTemplate Sendet Nachrichten an RabbitMQ
     */
    public MessageController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Nimmt eine Nachricht an, vergibt ID und Sendezeitpunkt und verschickt sie.
     * ID und Zeit entstehen hier auf dem Server, damit Datenbank und Stream
     * denselben Schluessel fuer dieselbe Nachricht fuehren.
     *
     * @param request Die Nachricht vom Client
     * @return Das Event inklusive erzeugter ID als Bestaetigung
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageEvent sendMessage(@RequestBody SendMessageRequest request) {
        String sender = bestimmeAbsender(request.sender());
        UUID nachrichtenId = UUID.randomUUID();
        Instant sendeZeitpunkt = Instant.now();

        MessageEvent event = new MessageEvent(
                nachrichtenId,
                request.roomId(),
                sender,
                request.content(),
                sendeZeitpunkt);

        // Weg 1: Persistenz. Default-Exchange mit Routing-Key chat.persist,
        // unveraendert aus Bewertung 1, damit der batch-writer weiter schreibt.
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_NAME, event);

        // Weg 2: Live-Zustellung. Fanout wertet keinen Routing-Key aus, deshalb leer.
        // Solange keine Instanz-Queue gebunden ist, verwirft der Broker diese Kopie.
        rabbitTemplate.convertAndSend(RabbitConfig.LIVE_EXCHANGE_NAME, "", event);

        return event;
    }

    /**
     * Bestimmt den Absendernamen fuer eine Nachricht.
     * Fehlt der Name oder besteht er nur aus Leerzeichen, wird anonymous gesetzt.
     * Die Verzweigung steht in einer eigenen Methode statt in einer Ternary-Kette,
     * damit jede Zeile einzeln vorlesbar ist (CLAUDE.md).
     *
     * @param senderAusAnfrage Der Name aus der Anfrage, darf null sein
     * @return Der Name, der in der Nachricht steht
     */
    private String bestimmeAbsender(String senderAusAnfrage) {
        if (senderAusAnfrage == null) {
            return "anonymous";
        }
        if (senderAusAnfrage.isBlank()) {
            return "anonymous";
        }
        return senderAusAnfrage;
    }
}