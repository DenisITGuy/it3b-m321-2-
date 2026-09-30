package ch.it3b.chat.api;

import java.util.UUID;

/**
 * Eingabeformat fuer POST /messages.
 * senderId und senderName sind optional: Fehlen sie, setzt der Dienst Platzhalter.
 *
 * @param roomId ID des Chat-Raums
 * @param content Nachrichtentext
 * @param senderId optionale Keycloak-Sub
 * @param senderName optionaler Anzeigename
 */
public record SendMessageRequest(UUID roomId, String content, String senderId, String senderName) {
}