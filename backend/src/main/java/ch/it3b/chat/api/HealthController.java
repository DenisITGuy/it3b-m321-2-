package ch.it3b.chat.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stellt einen einfachen Endpunkt bereit, um zu pruefen ob der Service lebt.
 * Wir brauchen ihn, um Docker, Nginx und Spring Boot gemeinsam zu testen,
 * bevor wir echte Chat-Logik bauen.
 */

// @RestController = Diese Klasse ist ein REST-Controller
// Spring Boot registriert sie automatisch als HTTP-Endpunkt-Handler
@RestController
public class HealthController {

    /**
     * Antwortet auf GET /api/health mit einem einfachen Lebenszeichen.
     * So sehen wir im Browser sofort, ob der ganze Weg durch Nginx funktioniert.
     */
    
    // @GetMapping = Diese Methode reagiert auf HTTP GET-Anfragen an die URL /api/health
    // GET = HTTP-Methode zum Abrufen von Daten (Standard für Browser-Anfragen)
    @GetMapping("/api/health")
    public String health() {
        // Gibt den Text "chat-service is alive" als HTTP-Antwort zurück
        // Spring Boot erstellt automatisch:
        //   HTTP Status: 200 OK
        //   Content-Type: text/plain
        //   Response Body: "chat-service is alive"
        return "chat-service is alive";
    }
}