package ch.it3b.chat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import javax.sql.DataSource;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Live-Zustellung gegen echten Broker und echte Datenbank.
 * Zwei Anwendungskontexte in einem JVM stehen fuer zwei Instanzen, jeder mit
 * eigener Live-Queue. Gelesen wird ueber HTTP, damit der ganze Weg bis zum
 * Socket geprueft wird. Der batch-writer ist nicht Teil dieses Tests, deshalb
 * werden die Zeilen fuer das Nachholen direkt geschrieben.
 */
class LiveZustellungIntegrationTest {

    private static RabbitMQContainer broker;
    private static PostgreSQLContainer<?> datenbank;
    private static ConfigurableApplicationContext instanzEins;
    private static ConfigurableApplicationContext instanzZwei;
    private static int portEins;
    private static int portZwei;
    private static HttpClient klient;
    private static JdbcTemplate jdbc;

    /**
     * Startet Broker, Datenbank und zwei Instanzen und legt die Tabelle an.
     * ddl-auto steht auf none, damit die Tabelle genau dem Schema aus
     * Bewertung 1 entspricht und nicht von der Entity abgeleitet wird.
     */
    @BeforeAll
    static void starteStack() {
        broker = new RabbitMQContainer("rabbitmq:3.12-management");
        broker.start();

        datenbank = new PostgreSQLContainer<>("postgres:16.1-alpine");
        datenbank.start();

        instanzEins = starteInstanz("chat.live.test-eins");
        instanzZwei = starteInstanz("chat.live.test-zwei");
        portEins = lesePort(instanzEins);
        portZwei = lesePort(instanzZwei);

        klient = HttpClient.newHttpClient();
        jdbc = new JdbcTemplate(instanzEins.getBean(DataSource.class));
        jdbc.execute("CREATE TABLE IF NOT EXISTS message ("
                + "id uuid PRIMARY KEY, "
                + "room_id uuid NOT NULL, "
                + "sender varchar(255) NOT NULL, "
                + "content text NOT NULL, "
                + "created_at timestamp(6) with time zone NOT NULL)");
    }

    /**
     * Schliesst die Kontexte und Container nach allen Tests.
     */
    @AfterAll
    static void stoppeStack() {
        if (instanzEins != null) {
            instanzEins.close();
        }
        if (instanzZwei != null) {
            instanzZwei.close();
        }
        if (datenbank != null) {
            datenbank.stop();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    /**
     * S3: Jede Nachricht kommt bei beiden Instanzen genau einmal an, die eines
     * Absenders in seiner Reihenfolge, und ein Client im fremden Raum bekommt
     * nichts. Eine globale Reihenfolge ueber beide Instanzen wird nicht geprueft:
     * RabbitMQ ordnet pro Verbindung, und die zwei Instanzen publishen ueber zwei
     * verschiedene Verbindungen. Der Broker darf sie deshalb verschachteln.
     *
     * @throws Exception Wenn ein HTTP-Aufruf scheitert
     */
    @Test
    void verteiltJedeNachrichtAnBeideInstanzenNurImPassendenRaum() throws Exception {
        UUID raumA = UUID.randomUUID();
        UUID raumB = UUID.randomUUID();

        SseSammler sammlerA1 = oeffneSammler(portEins, raumA, null);
        SseSammler sammlerA2 = oeffneSammler(portZwei, raumA, null);
        SseSammler sammlerB1 = oeffneSammler(portEins, raumB, null);
        warteBisVerbunden(sammlerA1);
        warteBisVerbunden(sammlerA2);
        warteBisVerbunden(sammlerB1);

        List<String> gesendetEins = new ArrayList<>();
        List<String> gesendetZwei = new ArrayList<>();
        for (int nummer = 1; nummer <= 10; nummer++) {
            String idEins = sendeNachricht(portEins, raumA, "eins", "eins-" + nummer);
            gesendetEins.add(idEins);
            String idZwei = sendeNachricht(portZwei, raumA, "zwei", "zwei-" + nummer);
            gesendetZwei.add(idZwei);
        }

        warteAufAnzahl(sammlerA1, 20);
        warteAufAnzahl(sammlerA2, 20);

        List<String> empfangenA1 = sammlerA1.getIds();
        List<String> empfangenA2 = sammlerA2.getIds();

        assertEquals(20, empfangenA1.size());
        assertEquals(20, empfangenA2.size());

        Set<String> eindeutigeIdsA1 = new HashSet<>(empfangenA1);
        assertEquals(20, eindeutigeIdsA1.size());
        assertTrue(eindeutigeIdsA1.containsAll(gesendetEins));
        assertTrue(eindeutigeIdsA1.containsAll(gesendetZwei));

        Set<String> eindeutigeIdsA2 = new HashSet<>(empfangenA2);
        assertEquals(20, eindeutigeIdsA2.size());

        assertEquals(gesendetEins, filtereTeilfolge(empfangenA1, gesendetEins));
        assertEquals(gesendetZwei, filtereTeilfolge(empfangenA1, gesendetZwei));
        assertEquals(gesendetEins, filtereTeilfolge(empfangenA2, gesendetEins));
        assertEquals(gesendetZwei, filtereTeilfolge(empfangenA2, gesendetZwei));

        assertEquals(0, sammlerB1.getDaten().size());

        sammlerA1.schliesse();
        sammlerA2.schliesse();
        sammlerB1.schliesse();
    }

    /**
     * S7: Nach einer Neuverbindung mit Last-Event-ID kommen genau die fehlenden
     * Nachrichten in Reihenfolge, keine doppelt, und danach wieder live.
     *
     * @throws Exception Wenn ein HTTP-Aufruf scheitert
     */
    @Test
    void holtNachNeuverbindungGenauDieFehlendenNachUndBleibtDanachLive() throws Exception {
        UUID raum = UUID.randomUUID();
        Instant startZeit = Instant.now();

        SseSammler ersterClient = oeffneSammler(portEins, raum, null);
        warteBisVerbunden(ersterClient);

        String ersteId = sendeNachricht(portEins, raum, "Tester", "erste");
        warteAufAnzahl(ersterClient, 1);
        assertEquals(ersteId, ersterClient.getIds().get(0));
        ersterClient.schliesse();

        // Der batch-writer laeuft hier nicht, deshalb wird die erste Nachricht
        // selbst persistiert: das Nachholen braucht sie als Anker in der Tabelle.
        schreibeZeile(UUID.fromString(ersteId), raum, "erste", Timestamp.from(startZeit));

        List<String> fehlendeIds = new ArrayList<>();
        for (int nummer = 1; nummer <= 50; nummer++) {
            UUID nachrichtenId = UUID.randomUUID();
            fehlendeIds.add(nachrichtenId.toString());
            Timestamp zeitpunkt = Timestamp.from(startZeit.plusMillis(nummer));
            schreibeZeile(nachrichtenId, raum, "fehlend-" + nummer, zeitpunkt);
        }

        SseSammler zweiterClient = oeffneSammler(portEins, raum, ersteId);
        warteAufAnzahl(zweiterClient, 50);

        assertEquals(fehlendeIds, zweiterClient.getIds());
        Set<String> eindeutigeIds = new HashSet<>(zweiterClient.getIds());
        assertEquals(50, eindeutigeIds.size());
        assertTrue(!zweiterClient.getIds().contains(ersteId));

        String liveId = sendeNachricht(portEins, raum, "Tester", "danach");
        warteAufAnzahl(zweiterClient, 51);
        assertEquals(liveId, zweiterClient.getIds().get(50));

        zweiterClient.schliesse();
    }

    /**
     * Startet eine Instanz mit eigener Queue und zufaelligem HTTP-Port.
     * Die Werte werden als Kommandozeilen-Argumente uebergeben, nicht als
     * Default-Properties: Default-Properties stehen in der Spring-Reihenfolge
     * unter application.yml, damit wuerde der Kontext zu postgres und rabbitmq
     * verbinden wollen, die es ausserhalb des Compose-Netzes nicht gibt.
     * System-Properties waeren zwar hoch genug, gelten aber fuer das ganze JVM
     * und wuerden beide Instanzen auf denselben Queue-Namen zwingen.
     *
     * @param queueName Name der Live-Queue dieser Instanz
     * @return Der laufende Kontext
     */
    private static ConfigurableApplicationContext starteInstanz(String queueName) {
        List<String> argumente = new ArrayList<>();
        argumente.add("--server.port=0");
        argumente.add("--live.queue.name=" + queueName);
        argumente.add("--spring.datasource.url=" + datenbank.getJdbcUrl());
        argumente.add("--spring.datasource.username=" + datenbank.getUsername());
        argumente.add("--spring.datasource.password=" + datenbank.getPassword());
        argumente.add("--spring.jpa.hibernate.ddl-auto=none");
        argumente.add("--spring.rabbitmq.host=" + broker.getHost());
        argumente.add("--spring.rabbitmq.port=" + broker.getAmqpPort());
        argumente.add("--spring.rabbitmq.username=" + broker.getAdminUsername());
        argumente.add("--spring.rabbitmq.password=" + broker.getAdminPassword());

        String[] argumentFeld = argumente.toArray(new String[0]);

        SpringApplicationBuilder bauer = new SpringApplicationBuilder(ChatApplication.class);
        return bauer.run(argumentFeld);
    }

    /**
     * Liest den tatsaechlich belegten HTTP-Port eines Kontexts.
     *
     * @param kontext Der laufende Kontext
     * @return Der Port
     */
    private static int lesePort(ConfigurableApplicationContext kontext) {
        WebServerApplicationContext webKontext = (WebServerApplicationContext) kontext;
        return webKontext.getWebServer().getPort();
    }

    /**
     * Oeffnet einen Stream und startet den Sammler in einem Daemon-Thread.
     *
     * @param port Port der Instanz
     * @param raumId Raum des Clients
     * @param lastEventId Zuletzt gesehene ID, darf null sein
     * @return Der Sammler
     * @throws Exception Wenn der Aufruf scheitert
     */
    private SseSammler oeffneSammler(int port, UUID raumId, String lastEventId) throws Exception {
        String adresse = "http://localhost:" + port + "/stream?roomId=" + raumId;
        HttpRequest.Builder bauer = HttpRequest.newBuilder(URI.create(adresse));
        bauer.GET();
        if (lastEventId != null) {
            bauer.header("Last-Event-ID", lastEventId);
        }
        HttpRequest anfrage = bauer.build();
        HttpResponse<InputStream> antwort = klient.send(anfrage, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, antwort.statusCode());

        SseSammler sammler = new SseSammler(antwort.body());
        Thread leser = new Thread(sammler);
        leser.setDaemon(true);
        leser.start();
        return sammler;
    }

    /**
     * Sendet eine Nachricht per POST und gibt die erzeugte ID zurueck.
     * Der Absender wird mitgegeben, damit die Reihenfolge pro Absender geprueft
     * werden kann.
     *
     * @param port Port der Instanz
     * @param raumId Raum der Nachricht
     * @param sender Absendername
     * @param inhalt Nachrichtentext
     * @return Die Nachrichten-ID aus der Antwort
     * @throws Exception Wenn der Aufruf scheitert
     */
    private String sendeNachricht(int port, UUID raumId, String sender, String inhalt) throws Exception {
        String json = "{\"roomId\":\"" + raumId + "\",\"sender\":\"" + sender + "\",\"content\":\"" + inhalt + "\"}";
        String adresse = "http://localhost:" + port + "/messages";
        HttpRequest.Builder bauer = HttpRequest.newBuilder(URI.create(adresse));
        bauer.header("Content-Type", "application/json");
        bauer.POST(HttpRequest.BodyPublishers.ofString(json));
        HttpRequest anfrage = bauer.build();
        HttpResponse<String> antwort = klient.send(anfrage, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, antwort.statusCode());

        String koerper = antwort.body();
        int anfang = koerper.indexOf("\"id\":\"") + 6;
        int ende = koerper.indexOf("\"", anfang);
        return koerper.substring(anfang, ende);
    }

    /**
     * Zieht aus der empfangenen Liste nur die IDs einer Sendegruppe heraus und
     * behaelt ihre Reihenfolge. Damit wird die Reihenfolge pro Absender geprueft,
     * ohne eine globale Reihenfolge zu verlangen, die der Broker nicht zusagt.
     *
     * @param empfangen Alle empfangenen IDs in Eingangsreihenfolge
     * @param gesendet Die IDs einer Sendegruppe in Sendereihenfolge
     * @return Die Teilfolge der empfangenen IDs dieser Gruppe
     */
    private List<String> filtereTeilfolge(List<String> empfangen, List<String> gesendet) {
        Set<String> gruppe = new HashSet<>(gesendet);
        List<String> teilfolge = new ArrayList<>();
        for (String id : empfangen) {
            if (gruppe.contains(id)) {
                teilfolge.add(id);
            }
        }
        return teilfolge;
    }

    /**
     * Schreibt eine Zeile in die Tabelle message.
     *
     * @param nachrichtenId ID der Nachricht
     * @param raumId Raum der Nachricht
     * @param inhalt Nachrichtentext
     * @param zeitpunkt Sendezeitpunkt
     */
    private void schreibeZeile(UUID nachrichtenId, UUID raumId, String inhalt, Timestamp zeitpunkt) {
        jdbc.update("INSERT INTO message (id, room_id, sender, content, created_at) VALUES (?, ?, ?, ?, ?)",
                nachrichtenId, raumId, "Tester", inhalt, zeitpunkt);
    }

    /**
     * Wartet bis der Stream den Kommentar gesendet hat.
     *
     * @param sammler Der Sammler
     * @throws Exception Wenn die Wartezeit ablaeuft
     */
    private void warteBisVerbunden(SseSammler sammler) throws Exception {
        long grenze = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < grenze) {
            if (sammler.istVerbunden()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Stream wurde nicht verbunden");
    }

    /**
     * Wartet bis genug Ereignisse angekommen sind.
     *
     * @param sammler Der Sammler
     * @param anzahl Erwartete Anzahl
     * @throws Exception Wenn die Wartezeit ablaeuft
     */
    private void warteAufAnzahl(SseSammler sammler, int anzahl) throws Exception {
        long grenze = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < grenze) {
            if (sammler.getIds().size() >= anzahl) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Nur " + sammler.getIds().size() + " von " + anzahl + " Ereignissen");
    }
}