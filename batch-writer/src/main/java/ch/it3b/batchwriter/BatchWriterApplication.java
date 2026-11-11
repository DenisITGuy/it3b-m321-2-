package ch.it3b.batchwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des batch-writer Dienstes.
 * Holt Nachrichten aus der Queue 'chat.persist' und schreibt sie gebatcht in PostgreSQL.
 */

// @SpringBootApplication = Die wichtigste Annotation in Spring Boot.
// Sie kombiniert drei Dinge:
// 1. @Configuration: Erlaubt @Bean-Methoden in dieser Klasse.
// 2. @EnableAutoConfiguration: Spring Boot konfiguriert sich automatisch 
//    (z.B. erkennt es RabbitMQ und PostgreSQL anhand der Dependencies).
// 3. @ComponentScan: Sucht im aktuellen Paket (und Unterpaketen) nach 
//    anderen Spring-Komponenten wie @Service, @Repository, @RestController.
@SpringBootApplication
public class BatchWriterApplication {

    /**
     * Main-Methode zum Starten der Spring Boot Anwendung.
     *
     * @param args Kommandozeilenargumente (werden nicht verwendet)
     */
    
    // main() = Der ganz normale Java-Startpunkt. 
    // Wenn man die JAR-Datei ausführt, startet die JVM genau hier.
    public static void main(String[] args) {
        // Übergibt die Kontrolle an Spring Boot.
        // Spring startet den ApplicationContext, verbindet sich mit RabbitMQ 
        // und der Datenbank, und startet den MessageConsumer (Listener).
        SpringApplication.run(BatchWriterApplication.class, args);
    }
}