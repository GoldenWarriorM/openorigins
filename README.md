# OpenOrigins

**OpenOrigins** is an unofficial re-implementation of **Origins Enhanced** behaviour for modern Minecraft versions, introducing multiple additional tweaks, fixes and features to enhance your gameplay experience on Origin Realms.

**AI Disclosure:** AI code generation was extensively used in the creation of this modification.

![Minecraft](https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-blue)
![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.5-green)
![Fabric API](https://img.shields.io/badge/Fabric%20API-0.161.0%2B26.2-green)
![Version](https://img.shields.io/badge/Version-2.0.0--beta1-orange)
![Side](https://img.shields.io/badge/Side-Client%20only-lightgrey)

---

## Contents

- [Features](#features)
  - [Gameplay & Visuals](#gameplay--visuals)
  - [Interface](#interface)
  - [Fixes](#fixes)
  - [Experimental](#experimental)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [License](#license)
- [Disclaimer](#disclaimer)

## Features

### Gameplay & Visuals

| Feature | Description |
| --- | --- |
| Smooth Animations | Interpolates armor-stand poses between server updates, including custom NPCs, cutscenes and windmills. |
| Discord Rich Presence | Shows the current location name reported by the server in your Discord status. |
| Model Culling | Expands the culling box of armor-stand rigs, display models and item frames (much more near the camera), so large builds do not disappear at screen edges. |
| Cutscene Enhancements | Locks FOV to 70 and freezes mouse look while you are in a cutscene (spectator mode). |
| Back Swag Render | Draws the back cosmetic on avatars, as reported by the server. |
| Moa Facing Fix | The server builds the Moa mount body facing backwards; this rotates it to the travel direction (visual only). |
| Allay Arms Fix | Custom allay arm models arrive detached; this shifts them back into place. |

### Interface

| Feature | Description |
| --- | --- |
| Server Command Hotkeys | Navigator menu on N, gestures on G; also bindable: spawn, realm, config, Badger HUD toggle, auction house, balloon teleports, entity extrapolation toggle. |
| Teleport Menu | Custom radial menu on B with spawn, home realm and balloon destinations using the server's own icons, plus docked homes and worlds panels with live reset timers. |
| Homes Panel | World-colored home icons, live reset timers and server-verbatim tooltips. Newly created homes appear instantly, no /homes re-fetch needed. |
| Badger Task HUD | Tracks your active Badger streak task on the HUD: requirements, inventory progress and turn-in state, pinnable to any screen corner. |
| Back Swag Slot | Read-only slot in the survival inventory showing the back item and its tooltip. |
| Hide Names | Hides entity name tags while the HUD is hidden (F1). |
| Auction House Helpers | Per-unit price line on multi-item listings, styled like the server's own price row. Single items are left untouched. |

### Fixes

| Feature | Description |
| --- | --- |
| Block Enhancements | Freezes client-side tripwire and note-block shapes so custom blocks built on them stop flickering. |
| Custom Item IDs | Advanced tooltips (F3+H) show the true originrealms: IDs of custom items. |
| Network Fixes | A dedicated settings tab for client/server agreement issues: stale scoreboard packets and duplicate registry entries no longer kick you, plus strict recipe-book matching for custom items. |

### Experimental

| Feature | Description |
| --- | --- |
| Helmet Cosmetic Slot | Adds a slot next to the helmet slot for the cosmetic; the real helmet stays visible in the helmet slot. |
| Entity Extrapolation | Renders custom mob bodies ahead of network updates (150 ms by default) to match the server's hitboxes (visual only). |

And more to come.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer.
2. Add [Fabric API](https://modrinth.com/mod/fabric-api).
3. Drop the OpenOrigins jar into your `mods` folder.

One jar works on both Minecraft **26.2** and **26.3**. Client-side mod — does nothing in singleplayer or on other servers.

## Building from source

```bash
./gradlew build
```

Requires Java 25. The resulting jar is placed in `build/libs`.

## License

MIT — see [LICENSE](LICENSE). Third-party assets and their licenses are listed
in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

OpenOrigins is not affiliated with, endorsed by, or associated with Origin Realms. Use at your own risk and in accordance with the server's rules.
