package ch.it3b.batchwriter.service;

import ch.it3b.batchwriter.model.MessageEvent;
import ch.it3b.batchwriter.repository.MessageRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageConsumer {

    private final MessageRepository messageRepository;

    public MessageConsumer(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @RabbitListener(queues = "chat.persist", containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void receiveBatch(List<MessageEvent> events) {
        if (events == null || events.isEmpty()) return;

        try {
            int count = 0;
            for (MessageEvent event : events) {
                if (event != null && event.getId() != null) {
                    messageRepository.saveIfNotExists(
                        event.getId().toString(),
                        event.getRoomId().toString(),
                        event.getSender(),
                        event.getContent(),
                        event.getCreatedAt()
                    );
                    count++;
                }
            }
            if (count > 0) {
                System.out.println("✅ Batch mit " + count + " Nachrichten geschrieben.");
            }
        } catch (Exception e) {
            System.err.println("❌ Fehler im Batch: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}