package ch.it3b.batchwriter.service;

import ch.it3b.batchwriter.model.MessageEvent;
import ch.it3b.batchwriter.repository.MessageRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consumer fuer die Queue 'chat.persist'.
 * Empfaengt Nachrichten in Batches und speichert sie idempotent in der Datenbank.
 * Erfuellt die Szenarien S3, S4, S5, S6 und S7.
 */
@Service
public class MessageConsumer {

    private final MessageRepository messageRepository;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param messageRepository Das JPA-Repository fuer die 'message' Tabelle
     */
    public MessageConsumer(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Empfaengt einen Batch von Nachrichten aus der Queue 'chat.persist'.
     * Wird automatisch von Spring AMQP aufgerufen, wenn genug Nachrichten gesammelt wurden.
     *
     * @param events Liste der empfangenen Nachrichten-Events
     */
    @RabbitListener(queues = "chat.persist", containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void receiveBatch(List<MessageEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            int processedCount = 0;
            for (MessageEvent event : events) {
                if (event == null || event.getId() == null || event.getRoomId() == null) {
                    continue;
                }
                
                messageRepository.saveIfNotExists(
                    event.getId().toString(),
                    event.getRoomId().toString(),
                    event.getSenderId(),
                    event.getSenderName(),
                    event.getContent(),
                    event.getSentAt()
                );
                processedCount++;
            }
            if (processedCount > 0) {
                System.out.println("✅ Batch mit " + processedCount + " Nachrichten erfolgreich in die DB geschrieben.");
            }
        } catch (Exception e) {
            System.err.println("❌ FEHLER BEIM SPEICHERN DES BATCHES: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}