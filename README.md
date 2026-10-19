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
- [Disclaimer](#disclaimer)

## Features

### Gameplay & Visuals

| Feature | Description |
| --- | --- |
| Armor Stand Interpolation | Animation smoothing network-wide, including custom NPCs, cutscenes and windmills — no more choppy animations. |
| Discord Rich Presence | Precise location tracking: your Discord status shows where you are on Origin Realms — spawn, resource worlds and realms. |
| Improved Culling | Large models no longer cut off abruptly: increased distance and viewing angles for armor-stand rigs and static Display-entity models. |
| Cutscene Enhancements | Cutscenes lock to FOV 70 with a frozen mouse, exactly as staged — no more camera drift mid-scene. |
| Back Swag Slot | The hidden back swag no longer obstructs your view in first person, plus a dedicated inventory slot so you always know what's on your back. |
| Moa Facing Fix | The server assembles the Moa mount body backwards — this turns it to face the direction of travel (visual only). |
| Allay Arms Fix | Custom allay arm models arrive detached; this shifts them back into place. |

### Interface

| Feature | Description |
| --- | --- |
| Server Command Hotkeys | Navigator menu on N, gestures on G; also bindable: spawn, realm, config, Badger HUD toggle, auction house, balloon teleports, entity extrapolation toggle. |
| Teleport Menu | Custom radial menu on B with spawn, home realm and balloon destinations using the server's own icons, plus docked homes and worlds panels with live reset timers. |
| Homes Panel | World-colored home icons, live reset timers and server-verbatim tooltips. Newly created homes appear instantly, no /homes re-fetch needed. |
| Badger Task HUD | Tracks your active Badger streak task on the HUD: requirements, inventory progress and turn-in state, pinnable to any screen corner. |
| Hide Names | In F1 all name tags are hidden — including quest icons and NPC interactions — for clean screenshots. |
| Auction House Helpers | Per-unit price line on multi-item listings, styled like the server's own price row. Single items are left untouched. |
| Boss Room Comfort | "You cannot do that here" denial spam in boss rooms is throttled: the first denial shows, repeats collapse to one per 10 seconds. |

### Fixes

| Feature | Description |
| --- | --- |
| Block Update Fixes | Eliminates the brief foliage flash when breaking blocks, and mimics vanilla client-side animations for custom block interactions. |
| Custom Item IDs | Advanced tooltips (F3+H) show the true originrealms: IDs of custom items. |
| Network Fixes | A dedicated settings tab for client/server agreement issues: stale scoreboard packets and duplicate registry entries no longer kick you, plus strict recipe-book matching for custom items. |

### Experimental

| Feature | Description |
| --- | --- |
| Helmet Cosmetic Slot | Shows your real helmet next to the cosmetic in the inventory, so you always know what's actually protecting your head. |
| Entity Extrapolation | Renders Grimms slightly ahead of their network updates for better hitbox matching. |

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

## Disclaimer

OpenOrigins is not affiliated with, endorsed by, or associated with Origin Realms. Use at your own risk and in accordance with the server's rules.
