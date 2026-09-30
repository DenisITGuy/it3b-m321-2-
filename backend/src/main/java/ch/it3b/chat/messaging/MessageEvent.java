package ch.it3b.chat.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Datenobjekt, das ueber RabbitMQ verschickt wird (Queue: chat.persist).
 * Die Felder entsprechen exakt PLANUNG.md Abschnitt 3.7.
 *
 * @param id Eindeutige ID der Nachricht (wichtig fuer Idempotenz / Szenario S5)
 * @param roomId ID des Chat-Raums
 * @param senderId Keycloak-Sub des Absenders
 * @param senderName Anzeigename des Absenders
 * @param content Nachrichtentext
 * @param sentAt Sendezeitpunkt
 */
public record MessageEvent(UUID id, UUID roomId, String senderId, String senderName, String content, Instant sentAt) {
}