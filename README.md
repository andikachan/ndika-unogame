# Enterprise Multiplayer UNO Engine

A production-grade, authoritative multiplayer backend engine for an online UNO game supporting **7 modular game variants**, built natively in Kotlin 2.x and Spring Boot 3.x WebFlux, integrated with Upstash Redis, fully automated with GitHub Actions CI/CD, and hardened with an enterprise zero-trust anti-cheat security model.

---

## 🚀 Key Features & Highlights

- **Multi-Variant Rule Engine (Strategy Pattern)**:
  1. **UNO Classic** (108 Cards) – Standard UNO color/number/action mechanics, +2 UNO challenge window penalty.
  2. **UNO Flip!** (112 Dual-Sided Cards) – Dual-sided deck with `LIGHT` and `DARK` state spaces. Dark cards include Draw Five (+5), Skip Everyone, and Wild Draw Color.
  3. **UNO Show 'Em No Mercy** (168 Cards) – Stacking (+2, +4, +6, +10 sequential penalties), 7-0 Hand Passing/Swapping, Mercy Elimination (instant elimination at $\ge 25$ cards), Discard All, and Wild Reverse Draw 4.
  4. **UNO Party!** – Scales 6-16 players, Speed Play / Jump-In (exact color + number match played out-of-turn stealing turn precedence), Point Taken, and Drawn Together linking.
  5. **UNO Showdown** (112 Cards) – Showdown Card trigger initiating a high-speed mini-duel with reaction timers and card dumping.
  6. **UNO All Wild** (112 Cards) – 100% Wild card deck featuring Wild Skip, Wild Skip 2, Wild Targeted Draw 4, and Wild Forced Swap.
  7. **Themed UNO (Modular Addons)** – Jurassic World (Danger Raptor escape cards) and Minecraft (Creeper penalty cards & Shield defense).

- **Zero-Trust Anti-Cheat & Sanitization Architecture**:
  - **Server-Authoritative Hidden State**: Opponents' secret hands and draw deck card sequences are **never** exposed in client payloads. Opponents only receive card counts.
  - **Sequence Number Validation (`seqId`)**: Monotonically increasing sequence validation prevents replay attacks.
  - **Upstash Sliding-Window Rate Limiting**: 10 requests/second per authenticated player/IP token bucket.
  - **Cryptographic Fairness**: Deck reshuffling powered by `java.security.SecureRandom` Fisher-Yates shuffle.

- **Infrastructure & Distributed State (Upstash Redis)**:
  - Distributed Locks (`SET NX EX`) to prevent race conditions during concurrent moves.
  - Redis Key Schema:
    - `room:{roomId}:meta` -> Hash: status, variant, direction, hostId, currentTurnIndex, activeSide
    - `room:{roomId}:deck:draw` -> List: secret card order
    - `room:{roomId}:deck:discard` -> List: discard history
    - `room:{roomId}:hand:{userId}` -> Encrypted secret hand
    - `room:{roomId}:penalties` -> Accumulated draw penalty state
    - `room:{roomId}:lock` -> Distributed lock key
    - `rate_limit:{key}` -> Sliding window rate counter

- **Automated CI/CD Pipeline (GitHub Actions)**:
  - Multi-stage build on Java 21 LTS (Temurin).
  - Automated Redis container service for integration tests.
  - Static code analysis with Detekt.
  - Full test suite execution across all variants and anti-cheat validations.
  - Executable boot JAR artifact generation.

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.0.20 / JVM 21
- **Framework**: Spring Boot 3.3.4 (Reactive WebFlux & WebSockets)
- **State & Cache**: Upstash Redis (Reactive Redis Template)
- **Security**: JJWT (HMAC-SHA256), Spring Security WebFlux
- **Build Tool**: Gradle (Kotlin DSL)
- **CI/CD**: GitHub Actions

---

## 🧪 Running Locally & Testing

### Prerequisites
- JDK 21 LTS
- Redis running on `localhost:6379` (or Upstash Redis endpoint)

### Run Tests
```bash
./gradlew test
```

### Run Application
```bash
./gradlew bootRun
```

---

## 📡 API & Real-time WebSocket Protocol

### 1. REST Endpoints
- `POST /api/auth/token` -> Returns JWT token for username
- `POST /api/rooms` -> Create a new room with chosen variant
- `GET /api/rooms/{roomId}` -> Retrieve room metadata

### 2. WebSocket Endpoint (`/ws/game`)
Send client JSON envelopes:
```json
{
  "type": "PLAY_CARD",
  "token": "<JWT_TOKEN>",
  "roomId": "ROOM123",
  "seqId": 1,
  "cardId": "uuid-card-id",
  "chosenColor": "RED"
}
```
Client receives player-specific sanitized game state:
```json
{
  "type": "GAME_STATE_UPDATE",
  "payload": {
    "roomId": "ROOM123",
    "variant": "NO_MERCY",
    "status": "AWAITING_MOVE",
    "activeSide": "LIGHT",
    "myHand": [ ... ],
    "opponents": [
      { "id": "p2", "username": "Bob", "cardCount": 7, "isUnoCalled": false }
    ],
    "drawPileCount": 154,
    "accumulatedDrawPenalty": 0
  }
}
```

---

## 📄 License
MIT License. Created by [andikachan](https://github.com/andikachan).
