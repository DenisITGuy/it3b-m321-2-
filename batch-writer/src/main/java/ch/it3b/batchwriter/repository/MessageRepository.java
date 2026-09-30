package ch.it3b.batchwriter.repository;

import ch.it3b.batchwriter.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Modifying
    @Query(value = "INSERT INTO message (id, room_id, sender, content, created_at) " +
                   "VALUES (cast(:id as uuid), cast(:roomId as uuid), :sender, :content, :createdAt) " +
                   "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void saveIfNotExists(@Param("id") String id,
                         @Param("roomId") String roomId,
                         @Param("sender") String sender,
                         @Param("content") String content,
                         @Param("createdAt") Instant createdAt);
}