# Changelog

## [2.0.0-beta1] — 2026-10-19

First **2.0** beta. Covers everything since **1.0.1** (362 commits,
134 files, +8734 / -601): a completely rebuilt configuration UI, a single jar
for **Minecraft 26.2 and 26.3**, the Auction Price Database, several new fixes
and a large performance and stability pass.

> Beta: features marked *Experimental* are still being tuned. Report issues
> with your game log.

### Highlights

- **One jar for Minecraft 26.2 and 26.3** — all version-divergent APIs are now
  resolved at runtime.
- **Auction Price Database** — browse, search and analyze auction sales with
  icons, rarity filtering and per-entry sale history.
- **Rebuilt settings UI** — left sidebar tabs, collapsible groups, search,
  per-row reset and inline hotkey rebinding.
- **New fixes** — Moa Facing Fix and Deny Spam Filter.

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

#### Auction House

- Per-item price line on multi-item listings, styled like the server's own price
  row. Single items are left untouched.

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

#### Hotkeys

- Auction house hotkey (`/ah`) and Auction Database screen hotkey.
- Entity Extrapolation live toggle (Debug category).

#### Experimental

- **Entity Extrapolation** — renders custom-mob bodies ahead of network updates
  to match server hitboxes: tunable forward time (ms), EMA velocity smoothing,
  per-frame damping, tracker cap/eviction and range filtering. Player mounts,
  fishing rigs and mount trees are skipped; custom item displays are filtered by
  item id.

#### Debug

- Outbox Monitor: bottom-right request/response state monitor with 1m / 30m /
  session rates (off by default).
- Verbose debug logging toggle (off by default).
- Rebindable scan/debug key for entity and container dumps, with persisted
  seen display keys.
- Clear data caches action (keeps the auction price database).

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
  Default hotkey is now `B`.
- **Culling:** expanded distance/angles for armor-stand rigs and static display
  models, including display model parts.
- **Network fixes:** Custom Item IDs moved into the Network fixes tab and strict
  recipe matching now resolves a single id for custom items (e.g. polymer).
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

- Badger tasks: plural tasks-complete messages are detected, partial/empty scans
  are ignored, the score sentinel and load hygiene are restored, the tier row is
  kept across tier-less accepts, and valid timers are no longer overwritten by
  stray large values.
- Homes: expired homes no longer inherit the new cycle timer, realm visits are
  filtered out, and blocked/fresh markers share one cache key.
- Background menu: orphaned fetch windows are swallowed and stale menus dropped
  on server close; only generic menus are swallowed, never real block containers.
- Duplicate registry entries and stale scoreboard packets no longer kick the
  client.
- Removed dead code, unused lang keys and temporary diagnostic probes/logging.

### Notes

- Requires **Java 25**, Fabric Loader **0.19.5+** and Fabric API.
- Client-side only: nothing changes in singleplayer or on other servers.
- This file starts at 2.0.0-beta1; 1.0.1 was published before a changelog was
  maintained.
