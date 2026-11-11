package ch.it3b.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des chat-service.
 * Diese Klasse startet den eingebetteten Web-Server und den Spring-Kontext.
 */

// @SpringBootApplication = Hauptklasse der Anwendung
// Kombiniert mehrere Spring-Features und startet den eingebetteten Web-Server
@SpringBootApplication
public class ChatApplication {

    /**
     * Wird von Java aufgerufen, wenn das Programm startet.
     * Uebergibt die Kontrolle sofort an Spring Boot.
     */
    
    // main-Methode = Startpunkt der Anwendung
    // Wird von Java automatisch aufgerufen, wenn die JAR gestartet wird
    public static void main(String[] args) {
        // Startet Spring Boot und den eingebetteten Web-Server
        // Spring scannt alle Controller, Beans und Konfigurationen
        SpringApplication.run(ChatApplication.class, args);
    }
}