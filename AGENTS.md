# AGENTS.md — ER Companion Dev Notes

Android companion app for **Pokémon Emerald Rogue (DepressoMocha/mocha fork)** running on an
**AYN Thor** dual-screen Android handheld (Android 13).

Top screen = RetroArch + mGBA core playing Emerald Rogue  
Bottom screen = this app  
Same device → same process, localhost I/O only

---

## Source References

| Resource | URL |
|---|---|
| ER mocha fork | https://github.com/DepressoMocha/emerogue (branch: `moka-dev`) |
| Move constants | `include/constants/moves.h` |
| Species constants | `include/constants/species.h` |
| Move data (power/type/cat) | `src/data/battle_moves.h` |
| Species graphics | `graphics/pokemon/<slug>/anim_front.png` |
| Vanilla Emerald structs | https://github.com/pret/pokeemerald |

Move IDs go up to **847** (Gen 9 Paldea).  
Species IDs go up to at least **1434** in current PokemonData.

---

## Save State Format

RetroArch on Android saves in **RASTATE** format:
- Magic: `RASTATE\x01`
- Chunked: `4-byte tag + 4-byte LE size + data`
- The `MEM` chunk is the raw mGBA core state (~528KB)

mGBA state layout:
- `EWRAM` starts at file offset `0x21000` (256KB = `0x40000` bytes)
- GBA EWRAM address `0x02000000` → file offset `0x21000`

### ER-Specific Addresses (mocha fork)
These differ from vanilla Emerald — ER uses extra EWRAM for its run data:

| Symbol | EWRAM offset | Notes |
|---|---|---|
| `gPlayerPartyCount` | `0x3777c` | u8, but ER can store 7 (last slot is padding) |
| `gPlayerParty` | `0x37780` | `gPlayerPartyCount + 4` |
| `gEnemyPartyCount` | `0x3777d` | 1 byte after player count |
| `gEnemyParty` | `gPlayerParty + 12*104` | Immediately after max player party buffer |
| `gBattlersCount` | `0x1839c` | u8, =2 during singles battle |
| `gBattlerPartyIndexes[0]` | `0x1839e` | u16, active player slot (0–5) |
| `gBattlerPartyIndexes[1]` | `0x183a0` | u16, active enemy slot |

**Note:** `gPlayerPartyCount` can be 7 in ER — ER reserves an extra slot. Clamp to valid mons
by checking `isValidMon()` (personality != 0, level 1–100, maxHP 1–9999, currentHP <= maxHP).

---

## Gen 3 Pokémon Struct (104 bytes = `struct Pokemon`)

```
Offset  Size  Field
0x00    4     personality (u32)
0x04    4     otId (u32) — full 32-bit trainer ID
0x08    10    nickname (Gen3 encoded)
...
0x20    48    encrypted substructs (4 × 12 bytes)
...
0x50    4     status condition (unencrypted)
0x54    1     level (unencrypted)
0x55    1     mail data
0x56    2     currentHP (unencrypted u16)
0x58    2     maxHP (unencrypted u16)
0x5A    2     attack
0x5C    2     defense
0x5E    2     speed
0x60    2     spAttack
0x62    2     spDefense
```

### Decryption
```
key = personality XOR otId
for each u32 in the 48-byte block: decrypted = encrypted XOR key
```

### Substructure Order
After decryption, the 4 × 12-byte blocks are in an order determined by `personality % 24`.
Use the **canonical** 24-entry table (see `Gen3PokemonParser.kt`).

**Substructure types:**
- `0` = Growth: species (u16), item (u16), experience (u32), friendship/ability/markings (u8s)
- `1` = Attacks: move1–4 (u16 each), PP1–4 (u8 each)
- `2` = EVs/Condition
- `3` = Misc (OT name, IVs, ribbons)

Species is bits 0–10 of the first u16 in substruct 0.  
Moves are four u16s at the start of substruct 1.

---

## In-Battle Detection

**Always use `gBattlersCount == 2` as the authoritative in-battle signal.**  
Do NOT use OT ID alone — enemy party memory persists after battle ends and will show stale trainer
mons out-of-battle.

`readInBattle()` in `SaveStateReader` checks `EWRAM+0x1839c == 2`.  
Enemy party is only populated in the UI when `inBattle == true`.

ER stores the **full trainer party** in save state slots after the player party (same memory block).
Both player and trainer mons can appear in slots 0–11.

**Filter rule:**
- Read `otId` (u32 at offset 4) from each parsed slot
- Player mons: `otId == playerOtId` (read from slot 0, which is always player's)
- Enemy/trainer mons: `otId != playerOtId`

This replaced the old "slot 6 = enemy lead" hardcode which was fragile.

---

## Sprites

URL pattern:
```
https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/<slug>/anim_front.png
```

`anim_front.png` is **64×128**: top 64px = front sprite, bottom 64px = back sprite.
Use `TopHalfCropTransformation` (Coil) to crop to top half only.

Slug = lowercase species name, spaces → `-`, dots/apostrophes removed.
Special cases: `nidoran-f`, `nidoran-m`, `mr-mime`, `ho-oh`, `tatsugiri` (all forms).

---

## JVM / Dex Size Limits

The JVM (and Android dex) has a **64KB limit per method**. Large `mapOf()` calls in a single
`object` initializer will compile but crash at runtime with `MethodTooLargeException`.

**Fix:** Split into chunked private functions + combine with `by lazy`:
```kotlin
private fun speciesNames_0(): Map<Int, String> = mapOf(/* 150 entries */)
private fun speciesNames_1(): Map<Int, String> = mapOf(/* 150 entries */)
// ...
private val SPECIES_NAMES: Map<Int, String> by lazy { speciesNames_0() + speciesNames_1() + ... }
```

**Chunk size:** 150 entries per function is safe. Already applied to:
- `SPECIES_NAMES` (1400+ entries, 9 chunks)
- `MOVE_NAMES` (847 entries, 6 chunks)
- `MOVE_DATA` (847 entries, 6 chunks)

---

## Known Issues / TODO

- **Damage calcs unreliable for enemy party** — enemy stats come from the save state but aren't
  always populated mid-battle (enemy struct may have zeroed stats until battle starts)
- **Storage permission** — `MANAGE_EXTERNAL_STORAGE` required on Android 13; the intent fires on
  first launch but user must manually grant it in Settings → Apps → ER Companion → All files access
- **No auto-save in RetroArch Android** — user must manually save state (hotkey or menu) before
  app can read party data; app polls file modification time
- **Active slot detection** — reads `gBattlerPartyIndexes[0]` from EWRAM; falls back to heuristic
  (first damaged mon) if not in battle
- **ER version sensitivity** — addresses above are confirmed for mocha `moka-dev` branch; a
  different ER build may shift EWRAM layout and require re-scanning

---

## Build & Release

```bash
./gradlew assembleDebug                          # debug APK (sideloadable, ~9MB)
./gradlew assembleRelease                        # release APK (~6MB, needs signing)
gh release create vX.Y.Z er-companion-vX.Y.Z-debug.apk --title "..." --notes "..."
```

**Versioning:** `versionCode = <minor*10 + patch>`, `versionName = "0.3.X"`  
**Signing:** debug keystore only (sideload use). No Play Store key configured.

---

## Architecture

```
savefile/SaveStateReader.kt   — finds + decompresses RASTATE, extracts EWRAM, scans for party
parser/Gen3PokemonParser.kt   — decrypts BoxPokemon structs, parses all fields
data/PokemonData.kt           — species names/types, move names/data (Gen 1–9)
calc/DamageCalculator.kt      — Gen 5+ damage formula with type chart
calc/BattleAISimulator.kt     — scores enemy moves using ER AI flags
MainViewModel.kt              — polls save state every 1s, filters player/enemy by OT ID
ui/MainScreen.kt              — Compose UI: battle layout (2-col) vs overworld (1-col)
MainActivity.kt               — permissions, crash reporter (shows stack on next launch)
```
