package ch.it3b.chat.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Nimmt die Kopie jeder Nachricht aus der Live-Queue dieser Instanz entgegen.
 * In diesem Schritt wird nur protokolliert, damit die Verteilung an zwei
 * Instanzen messbar ist. Der Versand an die Stream-Clients kommt danach dazu.
 */
@Component
public class LiveNachrichtenListener {

    private static final Logger LOG = LoggerFactory.getLogger(LiveNachrichtenListener.class);

    /**
     * Verarbeitet eine Nachricht aus der eigenen Live-Queue.
     * Der Body wird als Text gelesen und nicht neu serialisiert, weil spaeter
     * genau dieser JSON-Text als data: an den Client geht und dasselbe Format
     * wie auf chat.persist haben muss.
     *
     * @param nachricht Die rohe AMQP-Nachricht mit JSON-Body
     */
    @RabbitListener(queues = "${live.queue.name}", containerFactory = "liveListenerContainerFactory")
    public void empfangeNachricht(Message nachricht) {
        String jsonText = new String(nachricht.getBody(), StandardCharsets.UTF_8);
        LOG.info("Live-Kopie empfangen: {}", jsonText);
    }
}