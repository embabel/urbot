# Astrid: Astrology Chatbot

Astrid is a themed Urbot persona — a friendly, hippie Australian astrologer who believes the stars explain life.
She uses real-time astronomical calculations (Swiss Ephemeris) to generate natal charts and daily horoscopes,
backed by a graph-based memory of each user's birth details, interests, and life facts.

Activate with the Spring profile:

```bash
SPRING_PROFILES_ACTIVE=astrid ./mvnw spring-boot:run
```

## Architecture

The Astrid bot is a self-contained package in [`com.embabel.bot.astrid`](src/main/java/com/embabel/bot/astrid/).
It is discovered at startup via the `urbot.bot-packages` property set in
[`application-astrid.properties`](src/main/resources/application-astrid.properties):

```properties
urbot.bot-packages=com.embabel.bot.astrid
```

The package provides:

| Class | Purpose |
|---|---|
| [`AstridConfiguration`](src/main/java/com/embabel/bot/astrid/AstridConfiguration.java) | Spring `@Configuration` — registers users, relations, RAG documents, and the daily horoscope subagent |
| [`AstrologyTools`](src/main/java/com/embabel/bot/astrid/AstrologyTools.java) | `@LlmTool` methods the LLM can call: `calculateNatalChart`, `getSunSign`, `getHouseSystems` |
| [`DailyHoroscopeAgent`](src/main/java/com/embabel/bot/astrid/DailyHoroscopeAgent.java) | Multi-step Embabel `@Agent` — geocodes birth place, calculates natal chart, gets current transits, interprets |
| [`GeocodingService`](src/main/java/com/embabel/bot/astrid/GeocodingService.java) | Converts birth place names to latitude/longitude for chart calculation |

### Persona

The LLM persona is defined in [`astrid.jinja`](src/main/resources/prompts/personas/astrid.jinja).
Key traits: Australian accent, casual/friendly, uses emoji, avoids politics, knowledgeable about astrology but not physics.

### Domain Model

Astrid's [`Relations`](src/main/java/com/embabel/bot/astrid/AstridConfiguration.java) bean defines the entity types and relationships
extracted from conversations into the knowledge graph — pets, bands, books, goals, hobbies, places, organizations, and more.

## Astrology API Server

The astrology calculations are performed by [ryuphi/astrology-api](https://github.com/ryuphi/astrology-api),
a Node.js wrapper around the **Swiss Ephemeris** — the standard astronomical library used by professional astrology software.

It exposes a single endpoint:

```
GET /horoscope?time={ISO8601}&latitude={lat}&longitude={lng}&houseSystem={code}
```

Returns JSON with planetary positions (sign, degree, house, retrograde status), house cusps, and aspects.

### Docker Setup

The astrology API runs in Docker, gated behind a Compose profile so it only starts when needed.

**Start the astrology API (and Neo4j):**

```bash
docker compose --profile astrology up -d
```

This starts two containers:

| Container | Port | Purpose |
|---|---|---|
| `urbot-neo4j` | 7891 (Bolt), 8892 (HTTP) | Neo4j graph database for memory |
| `urbot-astrology-api` | 3000 | Swiss Ephemeris astrology API |

**Start Neo4j only (no astrology):**

```bash
docker compose up -d
```

The [Dockerfile](Dockerfile.astrology-api) clones the ryuphi/astrology-api repo, compiles the native Swiss Ephemeris
bindings via `node-gyp`, and runs the Express server on port 3000.

### Configuration

The API base URL is configured in [`application.yml`](src/main/resources/application.yml):

```yaml
astrology:
  api:
    base-url: ${ASTROLOGY_API_URL:http://localhost:3000}
```

Override with the `ASTROLOGY_API_URL` environment variable if the API is running elsewhere.

### Verify the API is Running

```bash
curl "http://localhost:3000/horoscope?time=2000-01-01T12:00:00Z&latitude=0&longitude=0&houseSystem=P"
```

You should get a JSON response with planetary positions, house cusps, and aspects.

## Daily Horoscope Agent

The [`DailyHoroscopeAgent`](src/main/java/com/embabel/bot/astrid/DailyHoroscopeAgent.java) is a multi-step Embabel agent
that generates personalized daily horoscopes. When a user asks "what do the stars say today?", Astrid:

1. **Retrieves birth details** from memory (birthdate, birth time, birth place)
2. **Geocodes** the birth place to coordinates via `GeocodingService`
3. **Calculates the natal chart** — planetary positions at the moment of birth
4. **Calculates current transits** — where the planets are right now
5. **Interprets** the transit-to-natal aspects with an LLM, producing a warm, personalized reading

The agent is registered as a `Subagent` tool in [`AstridConfiguration`](src/main/java/com/embabel/bot/astrid/AstridConfiguration.java),
so the LLM can invoke it whenever a horoscope is requested.

## Sample User Data

The [`data/users/`](data/users/) directory contains sample biographies for testing.
Use the **Learn** feature in the UI to ingest these — Astrid's memory pipeline will extract entities
(birth dates, places, pets, hobbies) into the knowledge graph, which then powers personalized horoscope readings.

## Quick Start

```bash
# 1. Start Docker services
docker compose --profile astrology up -d

# 2. Run Urbot with the Astrid profile
SPRING_PROFILES_ACTIVE=astrid ./mvnw spring-boot:run

# 3. Open the UI
open http://localhost:8080

# 4. (Optional) Ingest sample user data via Learn
#    Select a user, go to the Learn tab, paste or upload their bio
```
