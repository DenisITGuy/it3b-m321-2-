package ch.it3b.batchwriter;

import ch.it3b.batchwriter.model.MessageEvent;
import ch.it3b.batchwriter.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

// @SpringBootTest = Startet den kompletten Spring-Kontext für einen echten Integrationstest
// @Testcontainers = Aktiviert Testcontainers (startet echte Docker-Container für die Tests)
@SpringBootTest
@Testcontainers
class MessageConsumerIntegrationTest {

    // @Container = Startet einen echten PostgreSQL Docker-Container für diesen Test
    // Wir nutzen eine echte DB, nicht nur ein Mock, um das reale Verhalten zu testen
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    // Startet einen echten RabbitMQ Docker-Container
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

    // @DynamicPropertySource = Übergibt die zufälligen Ports/URLs der Docker-Container an Spring Boot
    // So weiß Spring Boot, wo die Test-DB und das Test-RabbitMQ laufen
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

    // @Autowired = Spring Boot injiziert diese Objekte automatisch für den Test
    @Autowired private RabbitTemplate rabbitTemplate; // Zum Senden von Nachrichten in die Queue
    @Autowired private MessageRepository messageRepository; // Zum Prüfen der DB (JPA)
    @Autowired private JdbcTemplate jdbcTemplate; // Zum Ausführen von rohem SQL (z.B. Tabelle löschen)

    // TEST 1: Prüft Szenario S5 (Idempotenz / Duplikate)
    @Test
    void s5_duplicateMessage_isSavedOnlyOnce() throws Exception {
        // Erstellt eine Test-Nachricht
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(), "testuser", "Duplikat-Test", Instant.now());
        
        // Schickt dieselbe Nachricht ZWEIMAL in die Queue
        rabbitTemplate.convertAndSend("chat.persist", event);
        rabbitTemplate.convertAndSend("chat.persist", event);

        // Wartet asynchron, bis die Nachricht verarbeitet wurde (max 5 Sekunden)
        long count = waitForCount(1, 20);
        Thread.sleep(500); // Kleine Pause, um sicherzugehen, dass kein 2. Durchgang mehr kommt
        
        // Prüft: Es darf nur genau 1 Nachricht in der DB sein (ON CONFLICT DO NOTHING greift)
        assertEquals(1, messageRepository.count(), "Duplikat nicht ignoriert!");
        messageRepository.deleteAll(); // Aufräumen
    }

    // TEST 2: Prüft Szenario S7 (Resilienz bei DB-Ausfall)
    @Test
    void s7_messageSurvivesDatabaseOutage() throws Exception {
        // Simuliert einen DB-Fehler, indem die Tabelle komplett gelöscht wird
        jdbcTemplate.execute("DROP TABLE message");
        
        // Schickt eine Nachricht in die Queue (der batch-writer wird beim Speichern fehlschlagen)
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(), "testuser", "Ausfall-Test", Instant.now());
        rabbitTemplate.convertAndSend("chat.persist", event);

        // Wartet kurz, damit der Consumer den Fehler merkt und die Nachricht in der Queue bleibt
        Thread.sleep(3000);
        
        // Stellt die DB wieder her (Tabelle neu erstellen)
        jdbcTemplate.execute("CREATE TABLE message (id uuid PRIMARY KEY, room_id uuid NOT NULL, sender varchar(50) NOT NULL, content text NOT NULL, created_at timestamp with time zone NOT NULL)");

        // Wartet, bis RabbitMQ die Nachricht erneut zustellt und sie erfolgreich gespeichert wird
        long count = waitForCount(1, 40);
        assertEquals(1, count, "Nachricht im Ausfall verloren!");
        messageRepository.deleteAll();
    }

    // Hilfs-Methode: Da der @RabbitListener asynchron im Hintergrund läuft, 
    // müssen wir pollend warten, bis die Nachricht wirklich in der DB angekommen ist.
    private long waitForCount(long min, int attempts) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            long c = messageRepository.count();
            if (c >= min) return c; // Erfolg: Erwartete Anzahl erreicht
            Thread.sleep(250); // 250ms warten und nochmal prüfen
        }
        return messageRepository.count();
    }
}