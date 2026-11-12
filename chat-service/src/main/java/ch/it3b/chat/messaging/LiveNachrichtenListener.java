package ch.it3b.chat.messaging;

import ch.it3b.chat.stream.StreamVerwaltung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Nimmt die Kopie jeder Nachricht aus der Live-Queue dieser Instanz entgegen
 * und gibt sie an die Stream-Clients des passenden Raums weiter.
 * Es laeuft genau ein Consumer, deshalb bleibt die Reihenfolge eines Absenders
 * erhalten: es gibt keinen zweiten Thread, der Ereignisse ueberholen koennte.
 */
@Component
public class LiveNachrichtenListener {

    private static final Logger LOG = LoggerFactory.getLogger(LiveNachrichtenListener.class);

    private final Jackson2JsonMessageConverter messageConverter;
    private final StreamVerwaltung streamVerwaltung;

    /**
     * Konstruktor fuer Dependency Injection.
     * Der Converter ist dieselbe Instanz, die auch den Versand serialisiert.
     *
     * @param messageConverter Wandelt die AMQP-Nachricht in ein MessageEvent
     * @param streamVerwaltung Haelt die offenen Verbindungen pro Raum
     */
    public LiveNachrichtenListener(Jackson2JsonMessageConverter messageConverter,
                                   StreamVerwaltung streamVerwaltung) {
        this.messageConverter = messageConverter;
        this.streamVerwaltung = streamVerwaltung;
    }

    /**
     * Verarbeitet eine Nachricht aus der eigenen Live-Queue.
     * Der JSON-Text fuer data: wird unveraendert aus dem Body genommen und nicht
     * neu serialisiert: nur so traegt der Stream dasselbe Format wie chat.persist
     * (createdAt als Epoch-Dezimalzahl). Geparst wird nur, um Raum und ID zu
     * bekommen. Gefiltert wird hier, weil nur an dieser Stelle bekannt ist,
     * welche Verbindung zu welchem Raum gehoert.
     *
     * @param nachricht Die rohe AMQP-Nachricht mit JSON-Body
     */
    @RabbitListener(queues = "${live.queue.name}", containerFactory = "liveListenerContainerFactory")
    public void empfangeNachricht(Message nachricht) {
        Object inhalt = messageConverter.fromMessage(nachricht);
        if (!(inhalt instanceof MessageEvent)) {
            LOG.warn("Nachricht ohne MessageEvent-Inhalt verworfen");
            return;
        }

        MessageEvent event = (MessageEvent) inhalt;
        String jsonText = new String(nachricht.getBody(), StandardCharsets.UTF_8);
        String nachrichtenId = event.id().toString();

        streamVerwaltung.sendeAnRaum(event.roomId(), nachrichtenId, jsonText);
    }
}