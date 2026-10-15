# What is OpenOrigins?

**OpenOrigins** is an unofficial re-implementation of **Origins Enhanced** behaviour for modern Minecraft versions, introducing multiple additional tweaks, fixes and features to enhance your gameplay experience on Origin Realms.

**AI Disclosure:** AI code generation was extensively used in the creation of this modification.

# Features:
## Armor Stand Interpolation
Implements animation smoothing network-wide, including on custom NPCs, cutscenes and even windmills! No more choppy animations!

## Server command Hotkeys
Open navigator menu by pressing N, press G to open gestures menu. Also bindable: teleport menu, spawn, realm, config, Badger HUD toggle, auction house (/ah), balloon teleports, entity extrapolation toggle.

## Teleport Menu
A radial menu with spawn, home realm and balloon destinations using the server's own icons, plus a docked homes panel and a worlds panel with live reset timers!

## Homes Panel
World-colored home icons, live reset timers and server-verbatim tooltips. Newly created homes appear instantly with local position and date, no /homes re-fetch needed.

## Block Update Fixes
Through a desync between client and server code, some foliage may briefly flash when being broken. By mimicking Origin Realms' behavior, that flash can be eliminated entirely! In addition, client-side animations for block interactions now mimic vanilla – particularly when right clicking a custom block.

## Discord Rich Presence
Through precise location tracking, your Discord rich presence can show off where you are on Origin Realms! Supporting spawn, resource worlds and realms

## Improved Culling
No longer will large models be abruptly cut off, increasing the distance and viewing angles for models to stop rendering. Covers both armor-stand rigs and static Display-entity models (windmills, hub decorations).

## Back Swag Slot
The hidden back swag ensures your cosmetics no longer obstruct your view when in first person, giving you an uninterrupted experience without sacrificing your drip! Plus a dedicated inventory slot so you always know what's on your back!

## Helmet Cosmetic Slot (Experimental)
Shows your real helmet next to the cosmetic in the inventory, so you always know what's actually protecting your head!

## Badger Task HUD
Tracks your active Badger streak task on the HUD: requirements, inventory progress and turn-in state, pinnable to any screen corner!

## Hide Names
When in F1, all name tags will now be hidden – including quest icons and NPC interactions – ensuring your screenshots are as clean as possible!

## Auction Ping
Never miss a sale: a sound and toast the moment your auction lot sells!

## Auction House Helpers
Per-unit price line on multi-item listings, styled exactly like the server's own price row. Single items are left untouched.

## Boss Room Comfort
"You cannot do that here" denial spam (e.g. bow shots in boss rooms) is throttled: the first denial always shows, repeats collapse to one per 10 seconds. Boss rooms are detected via the Lives boss-bar line.

## Cutscene Enhancements
Cutscenes lock to FOV 70 with a frozen mouse, exactly as staged – no more camera drift mid-scene!

## Custom Item IDs
Advanced tooltips (F3+H) show the true originrealms: IDs of custom items!

## Network Fixes
A dedicated settings tab for client/server agreement issues: stale scoreboard packets and duplicate registry entries no longer kick you, plus strict recipe-book matching for custom items!

## Moa Facing Fix
The server assembles the Moa mount body backwards – this turns it to face the direction of travel (visual only)!

## Entity Extrapolation (Experimental)
Renders Grimms slightly ahead of their network updates for better hitbox matching.

## Allay Arms Fix
Custom allay arm models arrive detached, this change shifts them back into place!

## And more to come!

Requires Fabric Loader, Fabric API and Minecraft 26.2. Client-side mod – does nothing in singleplayer or on other servers.

