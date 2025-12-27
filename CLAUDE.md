# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WoWChat-Turtle is a Discord-WoW chat bridge bot for Turtle WoW (turtlecraft.gg). It's a fork of [WoWChat](https://github.com/fjaros/wowchat) specifically tailored for the modded vanilla server. The bot runs clientlessly, connecting to both Discord and WoW game servers to relay chat messages bidirectionally.

## Build Commands

```bash
# Build the project (produces target/wowchat-1.3.8.zip)
mvn clean package

# Run the bot
java -jar wowchat.jar              # Uses default wowchat.conf
java -jar wowchat.jar myconfig.conf # Uses custom config file
```

**Requirements:** Java JDK 1.8, Maven 3

## Technology Stack

- **Language:** Scala 2.12.12 (compiles to Java bytecode)
- **Build:** Maven with scala-maven-plugin and maven-shade-plugin
- **Discord:** JDA 4.4.1 (Java Discord API)
- **Networking:** Netty 4.1.86 (non-blocking I/O)
- **Config:** Typesafe Config (HOCON format)

## Architecture

The bot implements a connection relay pattern:

```
Discord <--JDA--> [WoWChat Bot] <--Netty--> Realm Server --> Game Server
```

### Core Packages (`src/main/scala/wowchat/`)

- **WoWChat.scala** - Entry point. Initializes config, Discord connection, then realm/game connections with reconnect logic.

- **common/** - Global state and utilities
  - `Global.scala` - Singleton holding config, Discord/game handlers, and channel mappings (`discordToWow`, `wowToDiscord`, `guildEventsToDiscord`)
  - `Config.scala` - HOCON config parsing into typed case classes
  - `ReconnectDelay.scala` - Exponential backoff for reconnection

- **discord/** - Discord integration
  - `Discord.scala` - JDA event listener, message relay, guild notifications
  - `MessageResolver.scala` - Parses WoW item links, emojis, Discord @mentions

- **realm/** - Authentication to realm server
  - `RealmConnector.scala` - Netty bootstrap for realm connection
  - `RealmPacketHandler.scala` - SRP authentication protocol
  - `SRPClient.scala` / `BigNumber.scala` - Secure Remote Password implementation

- **game/** - Game server connection
  - `GameConnector.scala` - Netty bootstrap for game server
  - `GamePacketHandler*.scala` - Expansion-specific packet handling (vanilla through MoP)
  - `GameHeaderCrypt*.scala` - Expansion-specific encryption (RC4-based)
  - `warden/` - Anti-cheat response handling

- **commands/** - In-game and Discord commands (?who, ?gmotd)

### Expansion Version Pattern

The codebase uses class suffix naming for expansion-specific implementations:
- Base class: `GamePacketHandler`, `RealmPacketHandler`
- Expansions: `*TBC`, `*WotLK`, `*Cataclysm15595`, `*MoP18414`

Version selection happens in config (`wow.version`: 1.12.1, 2.4.3, 3.3.5, 4.3.4, 5.4.8).

## Configuration

Primary config: `src/main/resources/wowchat.conf` (HOCON format)

Key sections:
- **discord** - Bot token, dot command settings
- **wow** - Game version, realmlist, account credentials (supports env vars: `DISCORD_TOKEN`, `WOW_ACCOUNT`, `WOW_PASSWORD`, `WOW_CHARACTER`)
- **guild** - Notification settings (online/offline, promotions, etc.)
- **chat** - Channel relay configurations with direction, formatting, and filters
- **filters** - Java regex patterns for message filtering

## Key Implementation Details

- WoW chat messages are limited to 255 characters; longer Discord messages are split
- Item/spell links convert to classicdb/twinstar URLs based on expansion
- Discord requires PRESENCE INTENT, SERVER MEMBERS INTENT, and MESSAGE CONTENT INTENT enabled
- Platform setting (Mac/Windows) affects Warden anti-cheat responses
- Channel mappings are bidirectional hashmaps in `Global` for efficient lookups
