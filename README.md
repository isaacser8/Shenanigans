# Shenanigans

A Paper plugin for Minecraft minigames and mischief, built for Minecraft 26.3.
Playable from Bedrock Edition via Geyser.

## Minigames

Games run through a shared host that handles the lobby, arena, timers and cleanup.
Each game implements a `Minigame` interface, so adding a new one doesn't touch the host.

### Hide and Seek

Hiders are disguised as random mobs and scatter while the seekers are blinded and
rooted. When the hiding phase ends the seekers are released. Tagging a hider turns
them into a seeker. Seekers win when no hiders are left; hiders win on the timer.

## Playing

The player-facing flow is buttons and signs, not commands. Place a sign with an
action on line 1, and a button within 2 blocks of it:

| Sign line 1 | Line 2 | What the button does |
|---|---|---|
| `[join]` | game id | Selects that game and queues you |
| `[start]` | | Starts the round |
| `[find]` | | Finds arena candidates and tours the first |
| `[next]` | | Goes to the next candidate |
| `[use]` | | Sets the arena where you're standing |

A `[game]` sign selects a game when right-clicked directly.

## Commands

Everything above also has a command, mainly for admin use:

| Command | Description |
|---|---|
| `/mg games` | Lists available games |
| `/mg select <game>` | Selects a game |
| `/mg join [player]` | Joins the queue |
| `/mg start` | Starts the round |
| `/mg stop` | Ends the round and cleans up |
| `/mg setlobby` | Sets the lobby spawn (saved to config) |
| `/mg findarena` | Scans for arena candidates |
| `/mg list` | Lists candidates with coordinates and biome |
| `/mg visit <n>` | Teleports to candidate n |
| `/mg setarena [radius]` | Sets the arena, default radius 50 |

### Troll commands

| Command | Description |
|---|---|
| `/lightning <player>` | Strikes a player with lightning |
| `/creepyspawn <player>` | Spawns a creeper behind them |
| `/mobdisguise <player> <type>` | Disguises them as a mob |
| `/mobundisguise <player>` | Removes the disguise |

## Arena finding

`/mg findarena` scans for villages and samples biomes around the player, then asks
the selected game which spots suit it via `Minigame.isGoodArena(Location)`. Hide and
Seek wants cover; a game like spleef would want the opposite.

## Requirements

- Paper 26.3, Java 25
- [LibsDisguises](https://github.com/libraryaddict/LibsDisguises) and
  [PacketEvents](https://modrinth.com/plugin/packetevents) for disguises

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/`. Drop it into your server's `plugins/` folder.

## Notes

Developed against a local Paper server and tested from Minecraft Bedrock Edition
through [Geyser](https://geysermc.org) and Floodgate.