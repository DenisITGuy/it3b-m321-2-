package ch.it3b.chat.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stellt einen einfachen Endpunkt bereit, um zu pruefen ob der Service lebt.
 * Wir brauchen ihn, um Docker, Nginx und Spring Boot gemeinsam zu testen,
 * bevor wir echte Chat-Logik bauen.
 */
@RestController
public class HealthController {

    /**
     * Antwortet auf GET /api/health mit einem einfachen Lebenszeichen.
     * So sehen wir im Browser sofort, ob der ganze Weg durch Nginx funktioniert.
     */
    @GetMapping("/api/health")
    public String health() {
        return "chat-service is alive";
    }

    // TODO Uebung: Schreibe einen zweiten Endpunkt GET /api/info,
    // der den Namen des Services ("chat-service") und die Java-Version zurueckgibt.
    // Tipp: System.getProperty("java.version") liefert die Version als String.
}