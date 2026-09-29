package ch.it3b.batchwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des batch-writer Dienstes.
 * Holt Nachrichten aus der Queue 'chat.persist' und schreibt sie gebatcht in PostgreSQL.
 */
@SpringBootApplication
public class BatchWriterApplication {

    /**
     * Main-Methode zum Starten der Spring Boot Anwendung.
     *
     * @param args Kommandozeilenargumente (werden nicht verwendet)
     */
    public static void main(String[] args) {
        SpringApplication.run(BatchWriterApplication.class, args);
    }
}