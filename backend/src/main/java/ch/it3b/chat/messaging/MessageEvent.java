package ch.it3b.chat.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Datenobjekt, das ueber RabbitMQ verschickt wird (Queue: chat.persist).
 * Die Felder entsprechen exakt der eigenen PLANUNG.md (sender, createdAt).
 *
 * @param id Eindeutige ID der Nachricht (wichtig fuer Idempotenz / Szenario S5)
 * @param roomId ID des Chat-Raums
 * @param sender Benutzername des Absenders
 * @param content Nachrichtentext
 * @param createdAt Sendezeitpunkt
 */
public record MessageEvent(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
}