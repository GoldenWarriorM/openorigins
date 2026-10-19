# Changelog

All notable changes to OpenOrigins are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0-beta1] — 2026-10-19

First **2.0** beta. Covers everything since **1.0.1** (362 commits,
134 files, +8734 / -601): a completely rebuilt configuration UI, a single jar
for **Minecraft 26.2 and 26.3**, the Auction Price Database, Server Emoji,
several new fixes and a large performance and stability pass.

> Beta: features marked *Experimental* are still being tuned. Report issues
> with your game log.

### Highlights

- **One jar for Minecraft 26.2 and 26.3** — all version-divergent APIs are now
  resolved at runtime.
- **Auction Price Database** — browse, search and analyze auction sales with
  icons, rarity filtering and per-entry sale history.
- **Server Emoji** — server emoji codes with a picker, harvested from the
  server itself.
- **Rebuilt settings UI** — left sidebar tabs, collapsible groups, search,
  per-row reset and inline hotkey rebinding.
- **New fixes** — Moa Facing, Deny Spam Filter, Allay Arms, nametag rendering.

---

### Added

#### Auction Price Database *(Experimental)*

- Full auction-price browser screen with a searchable sale list and detail column.
- Volume-weighted auction statistics and a dedicated per-item detail view.
- Rarity sidebar with server-badge rarity buttons (prefix-only detection) and a
  separate bucket for badgeless items.
- Per-entry sale history, delete-entry and clear-database actions.
- Seller-exclusion list with an excluded column; seller attribution per sample
  with time-decayed de-duplication.
- Lot identity based on sample comparison with end tolerance and time-left
  samples; full-description item identity for accurate matching.
- Search by description, plain and display-case nicknames.
- Persisted auction item icons with per-row reconstruction cache.
- Ruby/gem price tails, wider rarity buttons and clamped layout on narrow windows.
- Experimental hint block and a database-format instability notice.

#### Server Emoji *(Experimental)*

- Emoji codes harvested from server suggestions and the server resource pack.
- Clickable emoji picker above the chat, matching the original UI 1:1.
- Automatic emoji conversion in chat and a live list in server order.
- Emoji unlocking via tab-list entries, with persistence across sessions.
- Toggle in HUD & Slots; bulk learning on join.

#### Configuration UI

- Left sidebar tab navigation: General, HUD & Slots, Client fixes,
  Network fixes, Experimental, Servers, Debug.
- Single settings feed with scroll-spy tabs and collapsible dependent subgroups
  (right-click a parent row to toggle, group glyph in the left gutter).
- Settings search and per-row reset buttons; global reset follows defaults.
- Inline hotkey rebinding rows with capture, ESC to cancel, and *Not Bound*
  display for unbound keys.
- Per-feature tooltips, experimental styling, and a stepper field whose width
  follows the maximum value.
- Servers tab: server allowlist plus handshake mode (handshake-verified Origin
  servers always apply).
- Visual HUD position editor for the Badger task HUD.

#### Client Fixes

- **Moa Facing Fix** — the server assembles the Moa mount backwards; it now
  faces its direction of travel (render-only, follows fresh vehicle yaw and
  guards null render state on spawns).
- **Deny Spam Filter** — the first "you cannot do that here" denial is shown,
  repeats are throttled inside boss rooms (boss-bar detection, dead zones for
  command-less areas, and a slow-down fallback).
- Network fixes subcategory with Custom Item IDs and strict recipe matching
  (single resolved id, e.g. polymer).
- Debug action to clear data caches (keeps the auction price database).

#### Hotkeys

- Auction house hotkey (`/ah`) and Auction Database screen hotkey.
- Balloon teleport hotkeys: blue, green, yellow, red.
- Badger HUD visibility toggle.
- Teleport menu now defaults to `B`; Navigator `N` and Gesture Menu `G` kept.
- Entity Extrapolation live toggle (Debug category).

#### Experimental

- **Entity Extrapolation** — renders custom-mob bodies ahead of network updates
  to match server hitboxes: tunable forward time (ms), EMA velocity smoothing,
  per-frame damping, tracker cap/eviction and range filtering. Player mounts,
  fishing rigs and mount trees are skipped; custom item displays are filtered by
  item id.
- **Break Sync** — server-driven custom block break prediction with two-tier
  break keys (block + tool aggregate fallback), proactive pacing and mismatch
  learning. Hidden from the UI and off by default.

#### Debug

- Outbox Monitor: bottom-right request/response state monitor with 1m / 30m /
  session rates (off by default).
- Verbose debug logging toggle (off by default).
- Rebindable scan/debug key for entity and container dumps, with persisted
  seen display keys.

#### Other

- Quick-join Origin Realms button on the title screen, with toggles to hide the
  title and pause buttons and an optional config button in the pause menu.
- Default home-lore template bundled for empty caches; home lore is cloned in
  the server's own style at creation time.
- Reconnect toast and background request infrastructure that yields to manual
  container activity.

### Changed

- **Dual-version support:** single jar for Minecraft **26.2 and 26.3**
  (`>=26.2 <26.4`). All divergent access goes through a runtime compatibility
  layer: input codes, `KEYBOARD` vs `KEYSYM`/`SCANCODE`, key event fields,
  `PoseStack` rotation and GUI blit pipelines/overloads.
- **Badger Task HUD:** requirements from lore, inventory progress and turn-in
  state, quest-stage tier row, deadline timer sourced from the "Next available"
  lore line, tier de-duplication per stage name, cut-corner backdrop toggle and
  cached row building. Accepts Delivered-marked scans and drops poisoned data.
- **Homes Panel:** world-colored icons, live reset timers, server-verbatim
  tooltips, capacity/max-slots hint. Newly created homes appear instantly;
  expired homes no longer inherit the cycle timer; realm visits are filtered out.
- **Teleport Menu:** radial menu with spawn, home realm and balloon destinations
  using the server's own icons (aspect ratio and UV mapping fixed), docked homes
  and worlds panels, scroll retention across reopens and empty-panel self-retry.
- **Auction helpers:** per-item price line styled like the server's own price
  row; per-sale ping matching (Auction Ping feature itself is parked/hidden).
- **Nametag rendering:** player tags hook into the vanilla submit funnel with an
  entity anchor from attachments; queued operations for not-yet-tracked entities
  and tag state cleared on entity unload.
- **Culling:** expanded distance/angles for armor-stand rigs and static display
  models, including display model parts.
- **Performance:** hot-path reflection caching, config-set caching, batched
  persistence, capped/throttled price DB writes and rescans, O(n) group trees,
  cached sale rows and formatters, single-pass extrapolation scans, cached
  renderer lookups and entity-type filtering.
- **UI polish:** sidebar/backdrop styling, gliding selection highlight, row
  geometry shared across entry types, tab/content width consistency.
- **Build/CI:** jar manifest stamped with `Implementation-Version: <ver>+<sha>`
  and `Git-Commit`; commit-tagged CI artifacts; dedicated tag-driven release
  workflow; actions upgraded off Node 20.

### Fixed

- Cross-wired renderer cache entries are now rejected and swapped to a no-op
  fallback; strict family sanity checks added.
- GUI blit pipelines and overloads resolved by full signature at runtime (fixes
  crashes on 26.3); SDL key-state reads are guarded instead of crashing.
- Escape key handling and unbound key display made version-agnostic.
- Badger scans: restored score sentinel and load hygiene, kept the tier row
  across tier-less accepts, and no longer overwrite valid timers with large
  values.
- Auction DB: icon reconstruction cached per row, layout clamped on narrow
  windows, draft/scroll state preserved across re-initialization.
- Orphaned background fetch windows are swallowed and stale menus dropped on
  server close; only generic menus are swallowed, never real block containers.
- Duplicate registry entries and stale scoreboard packets no longer kick the
  client.
- Stepper draft values survive rebuilds; group trees, reset rows and streaker
  labels corrected (styling and offset fixes).
- Removed dead code, unused lang keys and temporary diagnostic probes/logging.

### Removed

- Russian locale and Cyrillic string matching (locales are no longer bundled).
- Rocket fuel HUD/bar and the emoji block picker.
- `SpawnLoggerMixin` and all `TEMP`/probe-style diagnostics.
- Auction Ping and Break Sync are parked: kept in code, hidden from the UI and
  disabled by default.

### Notes

- Requires **Java 25**, Fabric Loader **0.19.5+** and Fabric API.
- Client-side only: nothing changes in singleplayer or on other servers.
- This file starts at 2.0.0-beta1; 1.0.1 was published before a changelog was
  maintained.
