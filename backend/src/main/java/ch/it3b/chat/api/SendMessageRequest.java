package ch.it3b.chat.api;

import java.util.UUID;

/**
 * Eingabeformat fuer POST /messages.
 *
 * @param roomId ID des Chat-Raums
 * @param content Nachrichtentext
 * @param sender optionaler Benutzername (wird auf "anonymous" gesetzt, falls leer)
 */
public record SendMessageRequest(UUID roomId, String content, String sender) {
}