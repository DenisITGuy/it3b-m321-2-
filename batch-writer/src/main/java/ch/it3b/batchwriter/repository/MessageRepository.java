package ch.it3b.batchwriter.repository;

import ch.it3b.batchwriter.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

// @Repository = Markiert das Interface als Spring Data Repository
// JpaRepository<Message, UUID> = Bietet automatisch Standard-Methoden 
// wie save(), findAll(), delete() für die Message-Entity (Primärschlüssel ist UUID)
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    // @Modifying = Sagt Spring Data JPA, dass diese Abfrage die Datenbank verändert (INSERT)
    // @Query = Definiert ein eigenes SQL-Statement
    // nativeQuery = true = Wir schreiben echtes SQL (hier PostgreSQL), kein JPQL
    @Modifying
    @Query(value = "INSERT INTO message (id, room_id, sender, content, created_at) " +
                   "VALUES (cast(:id as uuid), cast(:roomId as uuid), :sender, :content, :createdAt) " +
                   "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void saveIfNotExists(@Param("id") String id,
                         @Param("roomId") String roomId,
                         @Param("sender") String sender,
                         @Param("content") String content,
                         @Param("createdAt") Instant createdAt);
    
    // ERKLÄRUNG ZUR METHODE:
    // - @Param = Verbindet die Java-Parameter mit den Platzhaltern (:id, :roomId) im SQL
    // - cast(:id as uuid) = PostgreSQL-Funktion. Wandelt den String-Parameter in eine UUID um, 
    //                       weil die DB-Spalte den Typ UUID erwartet.
    // - ON CONFLICT (id) DO NOTHING = WICHTIG für Idempotenz (Szenario S5)! 
    //   Wenn eine Nachricht mit dieser ID schon in der DB ist, wird das INSERT ignoriert. 
    //   So verhindert man Duplikate, falls RabbitMQ eine Nachricht versehentlich doppelt schickt.
}