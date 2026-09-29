package ch.it3b.chat.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Datenobjekt, das ueber RabbitMQ verschickt wird (Queue: chat.persist).
 * Ein Record ist perfekt dafuer, da er unveraenderlich und kompakt ist.
 *
 * @param id Eindeutige ID der Nachricht (wichtig fuer Idempotenz / Szenario S5)
 * @param roomId ID des Chat-Raums
 * @param sender Absender (aus Keycloak Token)
 * @param content Nachrichtentext
 * @param createdAt Zeitstempel
 */
public record MessageEvent(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
}