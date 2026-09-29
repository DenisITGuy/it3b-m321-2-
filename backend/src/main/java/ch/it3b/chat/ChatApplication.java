package ch.it3b.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des chat-service.
 * Diese Klasse startet den eingebetteten Web-Server und den Spring-Kontext.
 */
@SpringBootApplication
public class ChatApplication {

    /**
     * Wird von Java aufgerufen, wenn das Programm startet.
     * Uebergibt die Kontrolle sofort an Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}