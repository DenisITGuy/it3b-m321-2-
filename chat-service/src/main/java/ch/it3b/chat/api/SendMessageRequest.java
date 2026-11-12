package ch.it3b.chat.api;

import java.util.UUID;

/**
 * Eingabeformat fuer POST /messages.
 *
 * @param roomId ID des Chat-Raums
 * @param content Nachrichtentext
 * @param sender optionaler Benutzername (wird auf "anonymous" gesetzt, falls leer)
 */

// Record = modernes Java-Feature (seit Java 14) für immutable Datenobjekte
// Erstellt automatisch: Konstruktor, Getter, equals(), hashCode(), toString()
// Weniger Boilerplate-Code als bei normalen Klassen
public record SendMessageRequest(UUID roomId, String content, String sender) {
    // Keine Methoden nötig - Record macht alles automatisch
    
    // Getter heißen wie die Felder (nicht getRoomId(), sondern roomId())
    // Alle Felder sind automatisch final (unveränderlich)
}