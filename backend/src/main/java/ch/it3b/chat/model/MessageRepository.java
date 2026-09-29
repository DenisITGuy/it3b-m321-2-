package ch.it3b.chat.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Liefert alle Nachrichten eines Raums, aelteste zuerst. */
    List<ChatMessage> findAllByRoomIdOrderByCreatedAtAsc(UUID roomId);
}