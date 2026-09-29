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

/**
 * Integrationstests fuer den MessageConsumer mit echter Queue und echter Datenbank.
 * Deckt die Pflicht-Tests fuer Szenario S5 (Duplikat) und S7 (Datenbank-Ausfall) ab.
 */
@SpringBootTest
@Testcontainers
class MessageConsumerIntegrationTest {

    /** Echter PostgreSQL-Container fuer die Tests. */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    /** Echter RabbitMQ-Container fuer die Tests. */
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

    /**
     * Verbindet die Spring-Anwendung mit den Testcontainers.
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Szenario S5: Dieselbe Nachricht wird zweimal in die Queue gelegt.
     * Erwartet: Genau eine Zeile in der Datenbank, kein Duplikat.
     *
     * @throws Exception falls das Warten unterbrochen wird
     */
    @Test
    void s5_duplicateMessage_isSavedOnlyOnce() throws Exception {
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(),
                "testuser", "Duplikat-Test", Instant.now());

        rabbitTemplate.convertAndSend("chat.persist", event);
        rabbitTemplate.convertAndSend("chat.persist", event);

        waitForCount(1, 20);
        Thread.sleep(500); // Karenzzeit: Ein Duplikat haette jetzt Zeit gehabt
        long count = messageRepository.count(); // NEU einlesen nach der Karenzzeit!

        assertEquals(1, count, "S5 verletzt: Duplikat wurde nicht ignoriert!");
        messageRepository.deleteAll();
    }

    /**
     * Szenario S7: Waehrend die Datenbank nicht schreibbar ist, trifft eine Nachricht ein.
     * Erwartet: Die Nachricht bleibt in der Queue und wird nach der Wiederherstellung zugestellt.
     *
     * @throws Exception falls das Warten unterbrochen wird
     */
    @Test
    void s7_messageSurvivesDatabaseOutage() throws Exception {
        // 1. Ausfall simulieren: Tabelle existiert nicht mehr
        jdbcTemplate.execute("DROP TABLE message");

        // 2. Nachricht waehrend des Ausfalls senden
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(),
                "testuser", "Ausfall-Test", Instant.now());
        rabbitTemplate.convertAndSend("chat.persist", event);

        // 3. Dem Consumer kurz beim Scheitern zusehen, dann DB wiederherstellen
        Thread.sleep(3000);
        jdbcTemplate.execute("CREATE TABLE message ("
                + "id uuid PRIMARY KEY, "
                + "room_id uuid NOT NULL, "
                + "sender varchar(50) NOT NULL, "
                + "content text NOT NULL, "
                + "created_at timestamp with time zone NOT NULL)");

        // 4. Erwartet: Nachricht wird trotzdem irgendwann zugestellt (Retry + Requeue)
        long count = waitForCount(1, 40);
        assertEquals(1, count, "S7 verletzt: Nachricht waehrend des Ausfalls verloren!");
        messageRepository.deleteAll();
    }

    /**
     * Wartet, bis mindestens die angegebene Anzahl Zeilen in der message-Tabelle liegt.
     *
     * @param minimum Mindestanzahl Zeilen
     * @param attempts Maximale Anzahl Poll-Versuche
     * @return Die zuletzt gelesene Anzahl
     * @throws InterruptedException falls das Warten unterbrochen wird
     */
    private long waitForCount(long minimum, int attempts) throws InterruptedException {
        long count = 0;
        for (int i = 0; i < attempts; i++) {
            count = messageRepository.count();
            if (count >= minimum) {
                return count;
            }
            Thread.sleep(250);
        }
        return count;
    }
}