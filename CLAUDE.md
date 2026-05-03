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
- **Distribution:** `mvn clean package -DskipTests` then run `启动服务端-GUI.bat` from project root

### Server Architecture (3-tier network)

The server runs three MINA-based network servers:

1. **LoginServer** (`handling/login/`) — Handles login/auth on port 9595 (configurable)
2. **ChannelServer** (`handling/channel/`) — Game world instances, 1 per channel on port 7575+ 
3. **CashShopServer** (`handling/cashshop/`) — Cash shop on port 8600

All share the same MINA codec filter (`MapleCodecFactory`) for the custom MapleStory packet protocol. Opcodes map via `recvops.properties` / `sendops.properties`.

### Threading Model (2026-05 refactored)

Each player's state mutations run on a single **Actor thread** (`PlayerActorExecutor`), eliminating race conditions:

```
IO thread (MINA)                     Timer thread (BUFF/MAP/WORLD)
     │                                       │
     ▼                                       ▼
┌─────────────────────────────────────────────────┐
│     PlayerActorExecutor (single-threaded)         │
│  All state changes for one player are serialized   │
└─────────────────────────────────────────────────┘
```

- `actor.execute()` — blocking submit (keeps calling thread synchronous)
- `actor.submit()` — fire-and-forget (for timer callbacks, packet sends)
- Components use `owner.getActor().execute()` before mutating state

### Key Packages

| Package | Responsibility |
|---|---|
| `client/` | Player state, inventory, skills, buddy list, anticheat |
| `client/component/` | Extracted character subsystems (Cooldowns, Diseases, Pets, Skills, Quests, Buffs) |
| `handling/` | Network servers, handlers, world/guild/family/party management |
| `server/` | Game logic (maps, monsters, NPCs, shops, items, quests, events) |
| `scripting/` | JavaScript engine for NPCs, portals, events, reactors |
| `tools/` | Packet construction, data I/O, WZ-to-SQL tools, `ConcurrentEnumMap` |
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

### MapleCharacter — Componentized

`MapleCharacter` is the central player class. After Phase 4 refactoring, it delegates to 6 components:

| Component | File | Responsibility |
|---|---|---|
| `CharacterCooldowns` | `client/component/CharacterCooldowns.java` | Skill cooldowns (ConcurrentHashMap-backed) |
| `CharacterDiseases` | `client/component/CharacterDiseases.java` | Debuff/disease state (ConcurrentEnumMap) |
| `CharacterPets` | `client/component/CharacterPets.java` | Pet spawning, hunger, unequip |
| `CharacterSkills` | `client/component/CharacterSkills.java` | Skills, macros, remaining SP |
| `CharacterQuests` | `client/component/CharacterQuests.java` | Quest status and info maps |
| `CharacterBuffs` | `client/component/CharacterBuffs.java` | Buff effects, combo, battleship, timers |
| `PlayerActorExecutor` | `client/PlayerActorExecutor.java` | Single-threaded actor for serialized state access |
| `DirtyTracker` | `client/DirtyTracker.java` | 13-category EnumSet tracking which data changed since last save |

Public API on MapleCharacter is backward-compatible — all methods still work, now delegate to components.

### DI & Configuration

- **Guice** is used for dependency injection. Modules are in `com/.../di/`.
- Config is loaded from `服务端配置.ini` (Properties format, read from external path)
- Database config (datasource.*), server IP/port, rate multipliers, feature toggles
- `Injectors.get(Class)` static service locator for legacy code without DI.

### Database

- **MySQL** via **EBean ORM** (v12)
- Entities in `com/.../domain/` — each `D*` class extends `io.ebean.Model`
- Query beans auto-generated (e.g., `QDAccount`) at compile time via annotation processor
- Initial schema: `db/ms079.sql`
- EBean migrations: `src/main/resources/dbmigration/`

### Persistence Model (2026-05 refactored)

`saveToDB()` uses **DirtyTracker** for incremental saves — only changed categories hit the DB:

```
Core row (always) → character.save()
Dirty sections (only if marked):
  INVENTORY, SKILLS, QUEST_STATUS, QUEST_INFO, SKILL_MACROS,
  COOLDOWNS, SAVED_LOCATIONS, ACHIEVEMENTS, BUDDIES,
  WISHLIST, TROCK_LOCATIONS, INVENTORY_SLOTS
Always sections:
  Account points, storage, CS, keylayout, mount, monsterbook
```

`saveToDB()` logs timing breakdown at INFO level: `[saveToDB] char=<name> total=<N>ms core=<N>ms always=<N>ms dirty=<N> sections:<breakdown>`

Auto-save runs every 30 min via `ApplicationStarter.autoSave()`, using `actor.submit()` for thread safety.

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

## Running the Server

### Startup

**Option 1: Distribution (recommended)**

```bash
mvn clean package -DskipTests   # Creates target/ms079-1.0-SNAPSHOT-dist.zip
# Extract to project root (auto-extracted by build), then double-click:
#   启动服务端-GUI.bat   (Swing GUI)
#   启动服务端-命令行.bat  (CLI)
```

The zip includes `ms079.jar` + all dependency jars in `lib/` + wz/ + 脚本/ + config.

**Option 2: Direct Java**

```bash
mvn clean compile -DskipTests
java -server -cp "./target/classes;./lib/*" -Dwzpath=wz \
  com.github.mrzhqiang.maplestory.MapleStoryApplication
```

The server reads config from `服务端配置.ini` in the working directory. This file is NOT in git.

### JVM System Properties

System properties can be used to toggle features or debug flags at runtime:

```bash
mvn exec:java -Dexec.mainClass="..." -Dkingmin.allow=true -Dcsopen.maxitems=0
```

**Important:** The server must be fully restarted for JVM property changes to take effect. A hot-reload is NOT sufficient.

### Packet Hex Dump

To dump raw hex of a specific packet for analysis, add temporary logging code at the packet send site:

```java
byte[] bytes = packet.getBytes();
System.out.println("Packet hex: " + HexTool.toString(bytes));
// or write to a file for large packets
FileOutputStream fos = new FileOutputStream("/tmp/packet_dump.txt", true);
fos.write(HexTool.toString(bytes).getBytes());
fos.close();
```

The `HexTool.toString(byte[])` method in `tools/HexTool.java` formats bytes as space-separated uppercase hex.

### MySQL Access

Database `ms079` on localhost:3306. Credentials in `服务端配置.ini` (datasource.* properties).

Useful queries:
- `SELECT id, name, level, job, str, dex, int_, luk, hp, max_hp, mp, max_mp, exp, map FROM characters WHERE name='<char>';`
- Skills: `SELECT s.* FROM skills s JOIN characters c ON s.character_id = c.id WHERE c.name='<char>';`
- Items: `SELECT i.* FROM inventory_items i JOIN characters c ON i.character_id = c.id WHERE c.name='<char>';`

## Client Crash Debugging

### ACCESS_VIOLATION

MapleStory v079 client crashes with ACCESS_VIOLATION when it reads memory that hasn't been allocated. Common causes:

1. **Malformed packet** — client reads past the end of the received buffer, or interprets a value incorrectly causing wrong-sized allocations
2. **Invalid data reference** — an ID in the packet (item, skill, map, etc.) doesn't exist in the client's WZ data, causing null pointer dereference
3. **Type/length mismatch** — server writes a different number of bytes than the client expects for a field (e.g., writing 4 bytes where client reads 2)
4. **Missing or extra fields** — packet structure doesn't match what the client expects (missing a required section, or extra data before expected fields)

### Key Packet Files for CS_OPEN

- `tools/packet/MTSCSPacket.java` — `warpCS()` method builds the CS_OPEN packet (~30KB)
- `tools/packet/PacketHelper.java` — `addCharStats()` (line 263), `addItemInfo()` shared helpers
- `client/PlayerStats.java` — `connectData()` writes STR/DEX/INT/LUK/HP/MP as shorts
- `handling/cashshop/handler/CashShopOperation.java` — calls `warpCS()` then `CSUpdate()` (4 follow-up packets)
- `sendops.properties` — opcode 0x83 = CS_OPEN
