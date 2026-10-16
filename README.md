# What Are the Odds Mod for Minecraft Fabric

![Modrinth]([https://img.shields.io/modrinth/dt/:f19Wo02n](https://modrinth.com/mod/what-are-the-odds))

## Overview

The "What Are the Odds" mod is a fun and engaging addition to Minecraft that brings a new layer of excitement to the
game. The mod can be used client or server side.

## Features

- Challenge another player to a round of What are the Odds.

### Usage

- `/odds` — opens an interactive dialog (via Minecraft's dialog UI) that walks you through challenging a
  player, accepting a challenge, or submitting your guess, depending on your current state.
- `/odds challenge <Player Name> <Message>` — challenge a player directly via command.
- `/odds respond <Number>` — accept a challenge and set the odds limit directly via command.
- `/odds guess <Number>` — submit your secret number directly via command (equivalent to typing the number
  in chat).
- `/odds cancel` — cancel a challenge you're currently part of (as challenger or opponent, accepted or not)
  and notify the other player. Also happens automatically if either player disconnects.

## Installation

1. **Install Fabric Loader:** Make sure you have Fabric Loader installed for your Minecraft version.
2. **Install the Fabric API**: Make sure to install the Fabric API Fabric mod.

2. **Download the Mod:** Download the latest version of the "What Are the Odds" mod from the releases section.

3. **Place in Mods Folder:** Move the downloaded mod JAR file into the "mods" folder in your Minecraft installation
   directory.

4. **Launch Minecraft:** Start the Minecraft launcher, select the Fabric profile, and launch the game.

## Compatibility

The "What Are the Odds" mod is designed for Minecraft Fabric and is compatible with other Fabric mods.

## Feedback and Support

If you encounter any issues or have suggestions for improvement, please visit
the Issues section.

## Development

### Building

`.jar` files are automatically built through the GitHub action. You can build the `.jar` by cloning the repo and
running the build gradle task.

#### Building manually
Execute the build task:
```bash
./gradlew build
```

### Releasing

Releases are cut with `release.sh`, which keeps the version in `gradle.properties`, the git tag, and the
published jar/Modrinth listing all in sync in one step. This is the only supported way to cut a release —
tagging manually will desync `mod_version` from the tag (as happened with `v1.1.0`/`v1.1.1`).

```bash
./release.sh v1.1.3
```

This:
1. Refuses to run if the working tree isn't clean, or if the tag already exists.
2. Builds the full version string from the given tag and the current `minecraft_version`
   (e.g. `v1.1.3` + `1.21.10` → `v1.1.3+mc1.21.10`).
3. Updates `mod_version` in `gradle.properties`, commits that change, tags the commit, and pushes both.

Pushing the tag triggers `build-and-release.yml`, which builds the jar, attaches it to a GitHub release, and
publishes it to Modrinth.

The version must be in `vX.X.X` form — bare `X.X.X` (no leading `v`) is rejected.
