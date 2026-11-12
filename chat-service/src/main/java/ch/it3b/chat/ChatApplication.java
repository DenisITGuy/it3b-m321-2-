package ch.it3b.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Startpunkt des chat-service.
 * Legt beim Start die Instanz-Kennung fest, aus der der Name der eigenen
 * Live-Queue entsteht, bevor der Spring-Kontext aufgebaut wird.
 */
@SpringBootApplication
public class ChatApplication {

    /**
     * Startet die Anwendung mit einer eigenen Instanz-Kennung.
     * Die Kennung wird als Default-Property gesetzt und nicht als System-Property:
     * Eine System-Property gilt fuer das ganze JVM und wuerde im Test zwei
     * Kontexte in einem JVM auf denselben Queue-Namen zwingen. Ausserdem steht
     * sie in der Spring-Reihenfolge ueber den Default-Properties und liesse sich
     * im Test nicht sauber ueberschreiben.
     *
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        String instanzKennung = UUID.randomUUID().toString();
        String queueName = "chat.live." + instanzKennung;

        Map<String, Object> vorgaben = new HashMap<>();
        vorgaben.put("live.queue.name", queueName);

        SpringApplication anwendung = new SpringApplication(ChatApplication.class);
        anwendung.setDefaultProperties(vorgaben);
        anwendung.run(args);
    }
}