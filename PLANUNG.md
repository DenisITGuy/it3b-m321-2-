# Planung Chat-System

## Inhaltsverzeichnis
1. [Technologie-Stack](#1-technologie-stack)
2. [Architektur](#2-architektur)
3. [Docker-Compose Setup](#3-docker-compose-setup)
4. [Microservices](#4-microservices)
5. [Datenbank-Design](#5-datenbank-design)
6. [Sicherheit & Authentication](#6-sicherheit--authentication)
7. [Offene Punkte](#7-offene-punkte)
8. [Verlauf](#8-verlauf)

---

## 1. Technologie-Stack

### Backend
- **Java 25** (LTS Version)
- **Spring Boot 3.2+** (vollständig kompatibel mit Java 21)
- **Spring WebSocket + STOMP** für Echtzeit-Kommunikation
- **Spring Security** mit OAuth2/OIDC für Keycloak-Integration
- **Spring Data JPA** für Datenbank-Zugriff

### Frontend
- **Web**:
  - **React 18+** mit TypeScript (empfohlen)
  - Alternative: **Vaadin** (Full-Stack Java)
  - **WebSocket Client** für Echtzeit-Kommunikation
- **Desktop**:
  - **JavaFX** (empfohlen)
  - Alternative: **Electron** mit Web-Technologien

### Identity & Access Management
- **Keycloak** (Latest Version)
  - OAuth2 + OpenID Connect
  - JWT Token-basierte Authentication
  - Realm: `chat-realm`
  - Clients: `chat-web-client`, `chat-desktop-client`

### Message Queue
- **RabbitMQ 3.x** (empfohlen)
  - Einfache Konfiguration
  - Ausreichend für moderate bis hohe Last
  - Gute Spring Boot Integration
  - Management-UI verfügbar
- **Alternative**: Apache Kafka (bei sehr hohem Durchsatz >100k msgs/sec)

### Datenbanken
- **PostgreSQL 16** (primäre Datenbank)
  - User-Daten, Chat-Historie, Metadaten
- **Redis 7** (Caching & Sessions)
  - WebSocket-Sessions, Message Broadcasting, Rate Limiting, Caching

### Infrastructure
- **Docker** + **Docker Compose**
- **Nginx** als Reverse Proxy/Web-Server

---

## 2. Architektur

### High-Level Architektur-Diagramm

```
+------------------------------------------------------------------+
|                   Docker Network: chat-internal                  |
|                                                                  |
|  +--------------+     +--------------+     +----------------+   |
|  |              |     |              |     |                |   |
|  |  Web-App     |---->|   Backend    |---->|    Keycloak    |   |
|  |  (Nginx)     |     |   Service    |     |     (IdP)      |   |
|  +--------------+     +--------------+     +----------------+   |
|         |                    |                     |            |
|         |                    v                     |            |
|         |            +--------------+              |            |
|         |            |   RabbitMQ   |              |            |
|         |            +--------------+              |            |
|         |                    |                     |            |
|         |         +----------+----------+          |            |
|         |         |                     |          |            |
|         |    +----v-----+          +----v-----+    |            |
|         |    |PostgreSQL|          |   Redis  |    |            |
|         |    +----------+          +----------+    |            |
|         |                                          |            |
|  +------v------+                                                |
|  | Localhost   |  <- NUR die Web-App ist von aussen erreichbar |
|  | 127.0.0.1   |                                                |
|  +-------------+                                                |
+------------------------------------------------------------------+
```

### Kommunikationsfluss
1. **User -> Web-App**: Browser verbindet sich mit Nginx (localhost:80/443)
2. **Web-App -> Backend**: REST/WebSocket Requests über internes Docker-Netzwerk
3. **Backend -> Keycloak**: Authentication/Authorization via OAuth2/OIDC
4. **Backend -> RabbitMQ**: Asynchrone Nachrichtenverarbeitung
5. **Backend -> PostgreSQL**: Datenpersistenz
6. **Backend -> Redis**: Caching, Sessions, Pub/Sub

### API-Endpoints (geplant)

**Authentication:**
- `GET /api/auth/login` -> Redirect zu Keycloak
- `GET /api/auth/callback` -> OAuth2 Callback
- `POST /api/auth/logout` -> Logout

**Chat:**
- `GET /api/chat/rooms` -> Alle Chat-Räume
- `GET /api/chat/rooms/{id}/messages` -> Nachrichten eines Raums
- `POST /api/chat/rooms/{id}/messages` -> Nachricht senden
- `WS /ws-chat` -> WebSocket Endpoint für Echtzeit-Chat

**Users:**
- `GET /api/users/me` -> Aktuelle User-Info
- `GET /api/users/{id}` -> User-Details
- `PUT /api/users/me` -> Profil aktualisieren

---

## 3. Docker-Compose Setup

### docker-compose.yml

```yaml
version: '3.8'

services:
  # Nginx Web-App (NUR ueber localhost erreichbar)
  web-app:
    image: nginx:alpine
    container_name: chat-web-app
    ports:
      - "127.0.0.1:80:80"
      - "127.0.0.1:443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./web-app/build:/usr/share/nginx/html:ro
    networks:
      - chat-internal
    depends_on:
      - backend
    restart: unless-stopped

  # Backend Service (Spring Boot)
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: chat-backend
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chat_db
      - SPRING_DATASOURCE_USERNAME=chat_user
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_RABBITMQ_HOST=rabbitmq
      - SPRING_RABBITMQ_USERNAME=${RABBITMQ_USER}
      - SPRING_RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
      - KEYCLOAK_URL=http://keycloak:8080
      - KEYCLOAK_REALM=chat-realm
      - KEYCLOAK_CLIENT_ID=chat-backend-client
      - KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
    networks:
      - chat-internal
    depends_on:
      postgres:
        condition: service_healthy
      keycloak:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      redis:
        condition: service_started
    restart: unless-stopped

  # Keycloak Identity Provider
  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    container_name: chat-keycloak
    command: start-dev
    environment:
      - KEYCLOAK_ADMIN=admin
      - KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
      - KC_DB=postgres
      - KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak_db
      - KC_DB_USERNAME=keycloak_user
      - KC_DB_PASSWORD=${POSTGRES_PASSWORD}
    networks:
      - chat-internal
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

  # RabbitMQ Message Queue
  rabbitmq:
    image: rabbitmq:3.12-management
    container_name: chat-rabbitmq
    environment:
      - RABBITMQ_DEFAULT_USER=${RABBITMQ_USER}
      - RABBITMQ_DEFAULT_PASS=${RABBITMQ_PASSWORD}
    networks:
      - chat-internal
    restart: unless-stopped

  # PostgreSQL Database
  postgres:
    image: postgres:16.1-alpine
    container_name: chat-postgres
    environment:
      - POSTGRES_DB=chat_db
      - POSTGRES_USER=chat_user
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    networks:
      - chat-internal
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chat_user -d chat_db"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # Redis Cache
  redis:
    image: redis:7.2-alpine
    container_name: chat-redis
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    networks:
      - chat-internal
    restart: unless-stopped

networks:
  chat-internal:
    driver: bridge

volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
```

**Wichtig:** Alle Services liegen im internen Docker-Netzwerk `chat-internal` und erreichen sich gegenseitig über ihre Service-Namen (`backend`, `keycloak`, `rabbitmq`, `postgres`, `redis`). **Nur** die Web-App bindet an `127.0.0.1` (localhost) und ist damit als einzige Komponente von außen erreichbar.

### .env Datei (Template)

```bash
# Database
POSTGRES_PASSWORD=change_me_postgres

# Keycloak
KEYCLOAK_ADMIN_PASSWORD=change_me_admin
KEYCLOAK_CLIENT_SECRET=change_me_client_secret

# RabbitMQ
RABBITMQ_USER=rabbitmq_user
RABBITMQ_PASSWORD=change_me_rabbit
```

---

## 4. Microservices

### Service 1: Web-App (Nginx)
**Zweck**: Reverse Proxy und Static File Server
- Port 80/443 (nur localhost)
- Reverse Proxy zu Backend
- SSL-Terminierung, Static Files (React Build)
- WebSocket-Upgrade unterstützen
- Rate Limiting, Security Headers (CSP, HSTS)

### Service 2: Backend (Spring Boot)
**Zweck**: Hauptanwendungslogik
- Module: `chat-service`, `user-service`, `auth-service`, `websocket-config`
- Spring Security mit OAuth2 Resource Server
- Spring WebSocket + STOMP
- Spring Data JPA, Spring AMQP (RabbitMQ), Spring Data Redis

### Service 3: Keycloak (Identity Provider)
**Zweck**: Authentication & Authorization
- Realm: `chat-realm`
- Clients:
  - `chat-web-client` (Public, Authorization Code Flow mit PKCE)
  - `chat-backend-client` (Confidential, Client Credentials)
- Roles: `USER`, `MODERATOR`, `ADMIN`

### Service 4: RabbitMQ
**Zweck**: Asynchrone Nachrichtenverarbeitung
- Exchange: `chat.exchange` (Topic Exchange)
- Queues:
  - `chat.messages.queue`: Persistente Nachrichten
  - `chat.notifications.queue`: Push-Benachrichtigungen
  - `chat.analytics.queue`: Analytics-Events
- Routing Keys: `message.send`, `message.deliver`, `user.online`, `user.offline`

### Service 5: PostgreSQL
**Zweck**: Datenpersistenz
- Datenbanken: `chat_db` (Anwendungsdaten), `keycloak_db` (Keycloak-Daten)

### Service 6: Redis
**Zweck**: Caching & Session-Management
- WebSocket-Sessions, Online-Status, Rate Limiting
- Pub/Sub für horizontale Skalierung
- Keys: `user:session:{userId}`, `user:online:{userId}`, `room:{roomId}:members`

---

## 5. Datenbank-Design

### Tabellen (chat_db)

```sql
-- Users (gespiegelte Daten von Keycloak)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    keycloak_id UUID UNIQUE NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(255),
    status VARCHAR(20) DEFAULT 'offline',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat Rooms
CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) DEFAULT 'public',
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Room Members
CREATE TABLE room_members (
    room_id UUID REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) DEFAULT 'member',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id)
);

-- Messages
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES users(id),
    content TEXT NOT NULL,
    message_type VARCHAR(20) DEFAULT 'text',
    file_url VARCHAR(255),
    edited BOOLEAN DEFAULT FALSE,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_room_id ON messages(room_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);
```

### Datenfluss: User erstellt Nachricht
1. Client -> Backend (WebSocket/REST)
2. Backend validiert User (JWT Token)
3. Backend prüft Room-Mitgliedschaft
4. Backend speichert Nachricht in PostgreSQL
5. Backend publisht Event zu RabbitMQ
6. Backend sendet via WebSocket an alle Room-Mitglieder
7. Client aktualisiert UI

---

## 6. Sicherheit & Authentication

### OAuth2 Flow
- **Authorization Code Flow mit PKCE** für den Web-Client
- JWT Tokens: Access Token 15 Min, Refresh Token 7 Tage
- Token-Validierung im Backend über Keycloak (JWKS Endpoint)

### Security-Maßnahmen
- **Transport**: HTTPS/TLS, HSTS, Secure Cookies
- **Authorization**: RBAC, Method-Level Security (`@PreAuthorize`)
- **Input-Validierung**: Prepared Statements, CSP, CSRF Protection
- **Rate Limiting**: 100 Req/Min pro User, 50 Messages/Min pro WebSocket

---

## 7. Offene Punkte

### A. Frontend-Technologie
**Status**: Entscheidung erforderlich
- **React + TypeScript** (empfohlen): modern, flexibel, große Community
- **Vaadin**: reines Java, schnellere Entwicklung, weniger flexibel
- **Vue.js**: einfacher zu lernen, kleinere Community als React

### B. Message Queue Strategie
**Status**: entschieden (RabbitMQ)
- RabbitMQ: einfacher, ausreichend für < 50k msgs/sec
- Kafka: verworfen für MVP (komplexer, Overkill), Migration später möglich

### C. Datei-Storage
**Status**: offen
- Optionen: Local Storage, MinIO (S3-kompatibel), Cloud Storage
- Empfehlung: MinIO für Development

### D. Monitoring & Logging
**Status**: offen
- Empfehlung: Phase 1 Basic Logging, Phase 2 Prometheus + Grafana, Phase 3 ELK

### E. Backup-Strategie
**Status**: offen
- Empfehlung: PostgreSQL täglich Full Backup + WAL, Redis RDB alle 15 Min, 30 Tage Aufbewahrung

### F. Skalierung
**Status**: Grundkonzept vorhanden
- Horizontal: mehrere Backend-Instanzen + Redis Pub/Sub für WebSocket-Clustering
- Vertikal: PostgreSQL Read-Replicas, Redis Cluster

### G. CI/CD-Pipeline
**Status**: offen (Empfehlung: GitHub Actions)

### H. Nachrichten-Aufbewahrung
**Status**: offen
- Empfehlung: unbegrenzt mit Soft Delete, GDPR-konforme Löschung eigener Nachrichten

### I. Desktop-App
**Status**: auf Phase 2 verschoben (Empfehlung: JavaFX)

---

## 8. Verlauf

*Dieser Abschnitt wurde von der KI verfasst und dokumentiert den Planungsprozess: gestellte Fragen, getroffene Entscheidungen und verworfene Varianten.*

### Gestellte Fragen an den Benutzer

**1. Message Queue Auswahl**
- Frage: "Erwarten Sie sehr hohe Nachrichtenraten (>100k/sec) oder reicht eine moderate Last?"
- Entscheidung: RabbitMQ als Default
- Begründung: einfacher zu konfigurieren, ausreichend für die meisten Use Cases; Kafka nur bei extrem hohem Durchsatz nötig

**2. Frontend-Framework**
- Frage: "Soll das Web-Frontend mit reinem Java (Vaadin) oder mit einem modernen JavaScript-Framework (React/Vue) entwickelt werden?"
- Entscheidung: noch offen
- Empfehlung: React + TypeScript für maximale Flexibilität

**3. Desktop-App Priorität**
- Frage: "Ist die Desktop-App für Phase 1 erforderlich oder kann sie später kommen?"
- Entscheidung: auf Phase 2 verschoben
- Begründung: MVP soll sich auf Web konzentrieren

**4. WebSocket vs. REST-Polling**
- Frage: "Benötigen Sie echte Echtzeit-Kommunikation oder reicht Polling?"
- Entscheidung: WebSocket + STOMP
- Begründung: echte Echtzeit-Kommunikation für Chat erforderlich

**5. Datenbank-Strategie**
- Frage: "Sollen alle Daten in einer DB oder getrennt pro Service liegen?"
- Entscheidung: zwei Datenbanken (chat_db + keycloak_db) auf einem PostgreSQL-Server
- Begründung: Einfachheit im Docker-Setup bei logischer Trennung

**6. Caching-Strategie**
- Frage: "Benötigen wir Redis von Anfang an oder erst bei Skalierungsbedarf?"
- Entscheidung: Redis von Anfang an
- Begründung: benötigt für Session-Management und WebSocket-Clustering

**7. Monitoring-Stack**
- Frage: "Soll Prometheus/Grafana von Anfang an integriert werden?"
- Entscheidung: noch offen (Empfehlung: Phase 2)

### Verworfene Varianten

1. **Keycloak mit H2-Datenbank** – verworfen (nicht produktionsgeeignet, Datenverlust bei Restart); gewählt: PostgreSQL
2. **Session-basierte Authentication** – verworfen (nicht skalierbar); gewählt: JWT Tokens
3. **REST-Polling für Chat** – verworfen (zu hohe Latenz, ineffizient); gewählt: WebSocket + STOMP
4. **Monolithische Architektur** – verworfen (schlechte Skalierbarkeit); gewählt: Microservices
5. **Jeder Service öffentlich exponiert** – verworfen (Sicherheitsrisiko); gewählt: nur Web-App auf localhost, Rest im Docker-internen Netzwerk
6. **Thymeleaf als Frontend** – verworfen (zu eingeschränkt für moderne Chat-UX); gewählt: React/Vue.js
7. **Apache Kafka für MVP** – verworfen (Overkill, komplexes Setup); gewählt: RabbitMQ mit Migrationspfad

### Technische Abwägungen

**Keycloak Setup:**
- ✅ Keycloak mit PostgreSQL-Backend
- ✅ Development-Mode (`start-dev`) für einfaches Setup
- ❌ Production-Mode verworfen (zu komplex für Development)

**Netzwerk-Architektur:**
- ✅ Einzelnes Docker-Bridge-Network (`chat-internal`)
- ✅ Nur Web-App (Nginx) auf localhost exponiert (`127.0.0.1:80/443`)
- ❌ Mehrere Networks verworfen (Over-Engineering für MVP)

**Authentication Flow:**
- ✅ OAuth2 Authorization Code Flow mit PKCE
- ✅ JWT-Tokens, Refresh-Token-Rotation
- ❌ Implicit Flow verworfen (unsicher)
- ❌ Resource Owner Password Flow verworfen (veraltet)

**Datenbank-Design:**
- ✅ PostgreSQL, UUIDs als Primary Keys, Soft Delete für Nachrichten
- ❌ MongoDB verworfen (SQL besser für relationale Chat-Daten)

**Message Queue Routing:**
- ✅ Topic Exchange, Durable Queues, Message Acknowledgment
- ❌ Fanout Exchange verworfen (zu unflexibel)

### Änderungen während der Planung

1. **Redis-Integration**: ursprünglich optional für Phase 2 → geändert: von Anfang an erforderlich (WebSocket-Clustering, Sessions)
2. **Health Checks**: ursprünglich nicht vorgesehen → geändert: Health Checks für alle Services (Observability, Recovery)
3. **Environment-Variablen**: ursprünglich hard-coded → geändert: alle Secrets in `.env` (Sicherheit)
4. **Docker-Volumes**: ursprünglich keine Persistenz → geändert: Volumes für PostgreSQL und Redis

### Nächste Schritte

1. User-Entscheidungen einholen (Frontend-Framework, Monitoring, CI/CD)
2. Docker-Compose finalisieren und Keycloak-Realm konfigurieren (Clients, Roles, Users)
3. Backend-Grundgerüst mit Spring Initializr erstellen, WebSocket + RabbitMQ implementieren
4. Testing: Unit Tests, Integration Tests (Testcontainers), Load Testing
5. Dokumentation: OpenAPI/Swagger, Developer Guide, Deployment Guide

---

**Dokument erstellt**: 28. August 2026
**Status**: Entwurf – wartet auf User-Entscheidungen
**Version**: 1.0

### Datenmodell (Abweichung vom Kurs-Standard)
Ich habe mich für folgende Spaltennamen entschieden, da sie semantisch klarer sind:
- `sender` (statt `sender_id`/`sender_name`): Enthält den Benutzernamen.
- `created_at` (statt `sent_at`): Standard-Timestamp-Name.