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

@SpringBootTest
@Testcontainers
class MessageConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

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

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private MessageRepository messageRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void s5_duplicateMessage_isSavedOnlyOnce() throws Exception {
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(), "testuser", "Duplikat-Test", Instant.now());
        
        rabbitTemplate.convertAndSend("chat.persist", event);
        rabbitTemplate.convertAndSend("chat.persist", event);

        long count = waitForCount(1, 20);
        Thread.sleep(500);
        assertEquals(1, messageRepository.count(), "Duplikat nicht ignoriert!");
        messageRepository.deleteAll();
    }

    @Test
    void s7_messageSurvivesDatabaseOutage() throws Exception {
        jdbcTemplate.execute("DROP TABLE message");
        
        MessageEvent event = new MessageEvent(UUID.randomUUID(), UUID.randomUUID(), "testuser", "Ausfall-Test", Instant.now());
        rabbitTemplate.convertAndSend("chat.persist", event);

        Thread.sleep(3000);
        jdbcTemplate.execute("CREATE TABLE message (id uuid PRIMARY KEY, room_id uuid NOT NULL, sender varchar(50) NOT NULL, content text NOT NULL, created_at timestamp with time zone NOT NULL)");

        long count = waitForCount(1, 40);
        assertEquals(1, count, "Nachricht im Ausfall verloren!");
        messageRepository.deleteAll();
    }

    private long waitForCount(long min, int attempts) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            long c = messageRepository.count();
            if (c >= min) return c;
            Thread.sleep(250);
        }
        return messageRepository.count();
    }
}