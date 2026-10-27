# Shenanigans

A Paper plugin for Minecraft mischief, built for Minecraft 26.3.

## Commands

| Command | Description |
|---|---|
| `/lightning <player>` | Strikes a player with lightning |
| `/creepyspawn <player>` | Spawns a creeper 3 blocks behind a player |
| `/mobdisguise <player> <type>` | Disguises a player as a mob |
| `/mobundisguise <player>` | Removes a disguise |

Players also get a custom obfuscated join message.

## Requirements

- Paper 26.3, Java 25
- [LibsDisguises](https://github.com/libraryaddict/LibsDisguises) and [PacketEvents](https://modrinth.com/plugin/packetevents) for the disguise commands

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/`. Drop it in your server's `plugins/` folder.

## Notes

Developed and tested on a local Paper server, played from Minecraft Bedrock via [Geyser](https://geysermc.org) and Floodgate.