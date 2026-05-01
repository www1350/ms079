# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

- **Build:** `mvn clean compile -DskipTests`
- **Package (with assembly):** `mvn clean package -DskipTests`
- **Run all tests:** `mvn test`
- **Run single test:** `mvn test -Dtest=com.github.mrzhqiang.maplestory.domain.DAccountTest`
- **Run specific method:** `mvn test -Dtest=DAccountTest#testCreate`
- **Skip tests during build:** `-DskipTests` or `-Dmaven.test.skip=true`

## Architecture Overview

This is a **MapleStory v079 private game server** written in Java 8 using Maven. It emulates the v079 ("Big Bang" era) MapleStory game server.

### Entry Points

- **CLI:** `com.github.mrzhqiang.maplestory.MapleStoryApplication` (main method)
- **GUI:** `gui.GUIApplication` (Swing-based launcher)
- Both use `ApplicationStarter` for common startup logic

### Server Architecture (3-tier network)

The server runs three MINA-based network servers:

1. **LoginServer** (`handling/login/`) — Handles login/auth on port 9595 (configurable)
2. **ChannelServer** (`handling/channel/`) — Game world instances, 1 per channel on port 7575+ 
3. **CashShopServer** (`handling/cashshop/`) — Cash shop on port 8600

All share the same MINA codec filter (`MapleCodecFactory`) for the custom MapleStory packet protocol. Opcodes map via `recvops.properties` / `sendops.properties`.

### Key Packages

| Package | Responsibility |
|---|---|
| `client/` | Player state, inventory, skills, buddy list, anticheat |
| `handling/` | Network servers, handlers, world/guild/family/party management |
| `server/` | Game logic (maps, monsters, NPCs, shops, items, quests, events) |
| `scripting/` | JavaScript engine for NPCs, portals, events, reactors |
| `tools/` | Packet construction, data I/O, WZ-to-SQL tools |
| `tools/packet/` | Per-category packet builders (Login, Mob, Pet, etc.) |
| `com/.../domain/` | EBean ORM entities (D-prefixed classes) |
| `com/.../service/` | Service layer (Account, World, Guild, Party, etc.) |
| `com/.../di/` | Guice DI modules (Configuration, Database, Mina) |
| `com/.../wz/` | WZ file parsing (.wz is MapleStory's resource format) |
| `com/.../config/` | `ServerProperties` POJO backed by `服务端配置.ini` |
| `com/.../auth/` | Authentication server logic |
| `KinMS/` | PvP and auto-event systems (third-party additions) |
| `constants/` | Game constants and server constants |
| `gui/` | Swing GUI for server management |

### DI & Configuration

- **Guice** is used for dependency injection. Modules are in `com/.../di/`.
- Config is loaded from `服务端配置.ini` (Properties format, read from external path)
- Database config (datasource.*), server IP/port, rate multipliers, feature toggles

### Database

- **MySQL** via **EBean ORM** (v12)
- Entities in `com/.../domain/` — each `D*` class extends `io.ebean.Model`
- Query beans auto-generated (e.g., `QDAccount`) at compile time via annotation processor
- Initial schema: `db/ms079.sql`
- EBean migrations: `src/main/resources/dbmigration/`

### Testing

- JUnit 4 (v4.13.2)
- EBean test module for in-memory DB testing (`ebean-test`)
- Tests: `src/test/java/com/github/mrzhqiang/maplestory/`
  - `DAccountTest` — DB entity persistence
  - `WzDataTest` / `WzResourceTest` — WZ resource loading
  - `DatabasePropertiesTest` — Config loading

### Key Libraries

- **Apache MINA 2** — NIO socket server for game protocol
- **EBean ORM** — Database with query beans
- **Guice 5** — DI
- **RxJava 3** — Reactive streams (WZ loading)
- **Nashorn** — JavaScript scripting engine for NPC/event scripts
- **Logback** — Logging
- **JSoup** — HTML parsing (for MapleStory API data)
- **Guava** — Utilities
