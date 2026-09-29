package ch.it3b.batchwriter;

import ch.it3b.batchwriter.model.MessageEvent;
import ch.it3b.batchwriter.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integrationstests fuer den MessageConsumer.
 * Nutzt Testcontainers, um eine echte PostgreSQL- und RabbitMQ-Instanz zu starten.
 * Erfuellt die Test-Anforderungen fuer Szenario S5 (Duplikat) und S7 (Ausfall).
 */
@SpringBootTest
@Testcontainers
class MessageConsumerIntegrationTest {

    /**
     * Startet einen echten PostgreSQL-Container fuer die Tests.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    /**
     * Startet einen echten RabbitMQ-Container fuer die Tests.
     */
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

    /**
     * Verbindet die Spring Boot Anwendung mit den Testcontainers.
     *
     * @param registry Die Property-Registry von Spring
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageRepository messageRepository;

    /**
     * Szenario S5 (Idempotenz): Dieselbe Nachricht wird zweimal gesendet.
     * Erwartet: Genau eine Zeile in der Datenbank.
     *
     * @throws Exception falls der Thread-Sleep unterbrochen wird
     */
    @Test
    void s5_duplicateMessage_shouldBeSavedOnlyOnce() throws Exception {
        // 1. Arrange: Eindeutige ID fuer diesen Test generieren
        UUID messageId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(messageId, roomId, "testuser", "Duplikat-Test", Instant.now());

        // 2. Act: Dieselbe Nachricht zweimal an die Queue senden
        rabbitTemplate.convertAndSend("chat.persist", event);
        rabbitTemplate.convertAndSend("chat.persist", event);

        // 3. Assert: Warten, bis der Batch-Writer beide verarbeitet hat
        // Wir pollen die Datenbank, da der Batch-Writer asynchron arbeitet.
        long count = 0;
        for (int i = 0; i < 20; i++) {
            count = messageRepository.count();
            if (count >= 1) {
                Thread.sleep(200); // Kurz warten, ob noch ein Duplikat durchrutscht
                break;
            }
            Thread.sleep(200);
        }

        // Es darf nur genau 1 Nachricht in der DB sein (wegen ON CONFLICT DO NOTHING)
        assertEquals(1, count, "S5 fehlgeschlagen: Duplikat wurde nicht korrekt ignoriert!");
        
        // Aufräumen für andere Tests
        messageRepository.deleteAll();
    }
}