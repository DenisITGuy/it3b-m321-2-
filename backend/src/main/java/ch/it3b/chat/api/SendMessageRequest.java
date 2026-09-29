package ch.it3b.chat.api;

import java.util.UUID;

/**
 * Eingabeformat fuer POST /messages.
 * Der sender ist optional: Fehlt er, setzt der Dienst einen Platzhalter.
 *
 * @param roomId ID des Chat-Raums
 * @param content Nachrichtentext
 * @param sender optionaler Absender
 */
public record SendMessageRequest(UUID roomId, String content, String sender) {
}