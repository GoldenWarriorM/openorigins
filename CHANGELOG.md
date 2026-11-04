# Changelog

## [2.0.0-beta1] — 2026-10-19

First **2.0** beta. Covers everything since **1.0.1**: a rebuilt settings menu,
support for Minecraft 26.2 and 26.3 in one jar, the Auction Price Database,
several new fixes and a large performance pass.

> Beta: features marked *Experimental* are still being worked on. Expect bugs and crashes.

## Highlights

- One jar for Minecraft 26.2 and 26.3.
- Auction Price Database: browse, search and check auction sales.
- Rebuilt settings menu with brand new look, tabs and collapsible groups
- New togglable fixes: Moa facing, model culling and block flicker.

## Added

#### Auction Price Database *(Experimental)*

- Browse and search auction sales, with item pictures and details.
- Price statistics per item, including average and volume-weighted prices.
- Rarity filter and a separate group for items without a badge.
- Sale history per item, plus delete and clear actions.
- Exclude sellers you don't want in the stats.
- A notice when the database format may still change.

#### Auction House

- Per-item price on multi-item listings, styled like the server's own row.

#### Configuration UI

- Rebuilt settings menu with tabs on the left: General, HUD & Slots, Client
  fixes, Network fixes, Experimental, Servers, Debug.
- Collapsible groups you can open with a right-click.
- Reset to default options
- Tooltips for every feature, plus a visual editor for the task HUD position.
- Servers tab with a server address list and automatic Origin server detection.

#### Client Fixes

- **Moa Facing Fix** — the Moa mount no longer walks backwards.

#### Hotkeys

- Auction house (`/ah`) and Auction Price Database hotkeys.
- Live toggle for Entity Extrapolation.

#### Experimental

- **Entity Extrapolation** — shows custom mobs slightly ahead to match their
  real hitboxes. Adjustable, with an optional hotkey to compare on the fly.

#### Debug

- Outbox Monitor: a small panel showing what the mod sends to the server
  (off by default).
- Verbose debug logging (off by default).
- Rebindable scan key for dumps.
- Clear saved data caches (keeps the auction price database).

#### Other

- Quick-join button on the title screen, plus options for the title and pause
  menu buttons.
- Default home tooltip text bundled for when no data is loaded yet.

## Changed

- One jar now works on Minecraft 26.2 and 26.3.
- **Badger Task HUD:** shows requirements and progress, the current
  stage tier and the reset timer, with an optional backdrop.
- **Homes:** reset timers, world colours and tooltips now match the server, and
  newly created homes have correct descriptions right away.
- **Teleport Menu:** Added default keybind `B`.
- **Network fixes:** Custom Item IDs and strict recipe matching gathered in one
  tab, and recipe matching now handles custom items better.
- **Performance:** less stutter in busy areas, faster menus.
- **Look and feel:** cleaner rows, a smoother selection highlight and more
  consistent sizing across menus.

## Fixed

- Culling: expanded boxes for armor stands, display models and item frames
  now actually work.
- Auction Price Database: recently sold items no longer affect prices.
- Badger tasks: fixed broken or missed scans, wrong reset timers and the stage
  tier row.
- Homes: fixed expired homes showing the wrong timer and world visits appearing
  in the list.
- Fixed leftover menu windows that could stay open or block other menus after
  leaving a server.
- Fixed duplicate registry entries and stale scoreboard packets kicking players.
- Removed leftover debug logging, unused text and temporary probes.

### Notes

- Requires **Java 25**, Fabric Loader **0.19.5+** and Fabric API.
- Client-side only: nothing changes in singleplayer or on other servers.
- This changelog starts at 2.0.0-beta1 and covers all changes since 1.0.1.
