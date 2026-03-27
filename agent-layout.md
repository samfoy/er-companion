# Task: Compact Out-of-Battle Layout — 2 Column, 3 Per Side

The out-of-battle screen currently shows a single scrollable column of mons. The user wants to fit all 6 in a 2x3 grid with minimal chrome.

## File
`app/src/main/java/com/ercompanion/ui/MainScreen.kt`

## What to Change

### 1. Remove/minimize the top chrome in out-of-battle mode
Currently the header shows: "ER Companion" title, Connected/Rescan status, "UDP LIVE" label, and a Debug accordion. This wastes ~120dp of space.
- Keep the connection status dot and Rescan button but make them more compact (small row, no title)
- Hide the Debug accordion in out-of-battle mode (or make it a small icon button in the corner)
- Goal: reduce top chrome to ~32dp in out-of-battle view

### 2. Switch out-of-battle party layout to 2-column grid
Find the section in `MainScreen` that handles the out-of-battle layout (the `else` branch after the battle layout — look for the single Column with `partyState.forEachIndexed`).

Replace the single column with a 2-column `Row`-based layout:
- Left column: party slots 0, 2, 4 (mons 1, 3, 5)
- Right column: party slots 1, 3, 5 (mons 2, 4, 6)
- Each column takes `weight(1f)`
- Each mon card should be compact — just name, level, types badge, HP bar
- No move list, no expanded view in out-of-battle grid

### 3. Compact mon card for the grid
Create a new `CompactPartyMonCard` composable (or reuse/adapt `BenchedMonChip`) that shows:
- Species name (bold, ~12sp)
- Level (right-aligned)
- Type badge(s)
- HP bar + HP numbers
- Nature indicator (small, one line)
- Ability name (small)
- Keep height ~90-100dp per card so 3 fit on screen

### 4. Keep the existing expanded PokemonCard available on tap
If the user taps a compact card, expand it to show moves/damage — use a dialog or navigate to a detail view, or just toggle to the full PokemonCard inline.

## Style
- Dark theme: background Color(0xFF1A1A2A) per card
- Spacing: 4dp between cards, 4dp padding

## Build & Verify
Run: `./gradlew assembleDebug`
Must compile clean.

Commit with: "feat: compact 2-column out-of-battle layout, all 6 mons visible"
Then run: openclaw system event --text "Done: Compact out-of-battle 2-column layout" --mode now
