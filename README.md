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
