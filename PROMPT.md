# ER Companion App — v0.2: Sprites, Damage Calcs, Recommended Builds

## Context

This is a continuation of v0.1 which is already built and compiles successfully. The APK exists at `app/build/outputs/apk/debug/app-debug.apk`. Do NOT rewrite the project from scratch — incrementally improve it.

## Current State

All source files exist and compile:
- `network/RetroArchClient.kt` — UDP socket client for RetroArch network commands
- `parser/Gen3PokemonParser.kt` — BoxPokémon decryption + party parsing
- `parser/AddressScanner.kt` — runtime EWRAM address scanner
- `data/PokemonData.kt` — species/move name maps
- `ui/MainScreen.kt` — Jetpack Compose dark UI, party panel
- `MainViewModel.kt` — StateFlow MVVM
- `MainActivity.kt` — entry point
- `gradle.properties` already has `android.useAndroidX=true` and `android.enableJetifier=true`
- `local.properties` points to `sdk.dir=/opt/homebrew/share/android-commandlinetools`

## What to Build in v0.2

### 1. Pokémon Sprites

**Source:** `https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/<name>/icon.png`

- Pokemon names in the URL are lowercase, e.g. `bulbasaur`, `charizard`, `mr-mime`
- Icon sprites are small (~32x32px), perfect for party panel
- Bundle a script to download sprites at build time, OR fetch at runtime and cache in app's files dir
- **Preferred: runtime fetch + disk cache** (keeps APK small, ER has 1000+ species)
  - On first load: fetch from GitHub raw URL, save to `filesDir/sprites/<name>.png`
  - Display with Coil (async image loading library)
  - Show a Poké Ball placeholder while loading

**Add Coil dependency** to `app/build.gradle.kts`:
```kotlin
implementation("io.coil-kt:coil-compose:2.5.0")
```

**Species name → URL slug mapping** — most names just lowercase, but handle edge cases:
- `nidoran-f` → `nidoran-f`
- `nidoran-m` → `nidoran-m`
- `mr-mime` → `mr-mime`
- `mime-jr` → `mime-jr`
- `farfetchd` → `farfetchd`
- Use the species name from `PokemonData.kt` lowercased and hyphenated

### 2. Damage Calculator (Gen5+ formula — confirmed from ER source)

The formula from `src/battle_util.c` in the DepressoMocha emerogue repo:

```c
// CalculateBaseDamage:
dmg = power * userFinalAttack * (2 * level / 5 + 2) / targetFinalDefense / 50 + 2

// Then modifiers applied in order:
// 1. Target modifier (0.75x if multi-target move)
// 2. Weather (1.5x for sun/rain boosted moves, 0.5x for weakened)
// 3. Critical hit (1.5x)
// 4. Random factor (85–100%)
// 5. STAB (1.5x, or 2.0x with Adaptability)
// 6. Type effectiveness (0x, 0.25x, 0.5x, 1x, 2x, 4x)
// 7. Burn (0.5x if attacker is burned and using physical move, unless Guts)
```

**Implement `DamageCalculator.kt`:**

```kotlin
data class DamageResult(
    val moveName: String,
    val minDamage: Int,       // at 85% roll
    val maxDamage: Int,       // at 100% roll
    val effectiveness: Float, // 0.0, 0.25, 0.5, 1.0, 2.0, 4.0
    val effectLabel: String,  // "", "Not very effective", "Super effective!", "No effect"
    val percentMin: Int,      // min as % of target maxHP
    val percentMax: Int,      // max as % of target maxHP
    val isStab: Boolean,
    val wouldKO: Boolean,     // percentMax >= 100
)

object DamageCalculator {
    fun calc(
        attackerLevel: Int,
        attackStat: Int,    // atk or spAtk depending on move category
        defenseStat: Int,   // def or spDef depending on move category
        movePower: Int,
        moveType: Int,      // type ID
        attackerTypes: List<Int>,
        defenderTypes: List<Int>,
        targetMaxHP: Int,
        targetCurrentHP: Int,
        isBurned: Boolean = false,
        weather: Int = 0,   // 0 = none
    ): DamageResult
}
```

**Type effectiveness table** — use the modern (Gen6+) chart ER uses:
- All 18 types × 18 types
- Include Fairy type
- Hardcode as a 2D array of floats

**Move data** — expand `PokemonData.kt` to include for each move:
- `power: Int` (0 for status moves)
- `type: Int` (type ID)
- `category: Int` (0=physical, 1=special, 2=status)

At minimum include the top 150 most common moves in Gen3-9 that appear in ER. Structure:
```kotlin
data class MoveData(val name: String, val power: Int, val type: Int, val category: Int)
val MOVE_DATA: Map<Int, MoveData>  // move ID → MoveData
```

### 3. UI: Damage Display

In `MainScreen.kt`, when a party slot is expanded:
- Show the 4 moves with damage calc results vs current enemy lead
- Format: `Earthquake — 84–99 dmg (42–50%) ⚡ Super effective!`
- Color code: red for SE, gray for NVE, white for neutral, dark for immune
- If no enemy party is scanned yet, show `— vs ?` placeholder
- Show type effectiveness label inline

### 4. Recommended Builds

**Source:** The ER Discord data indexed at `~/.openclaw/workspace/` via the er-discord-search skill.

Run this query at build time to generate a static JSON file bundled in the APK assets:

```bash
python3 ~/.openclaw/workspace/scripts/er_search.py "recommended build" --top-k 50
python3 ~/.openclaw/workspace/scripts/er_search.py "best moveset" --top-k 50
python3 ~/.openclaw/workspace/scripts/er_search.py "tier list" --top-k 30
```

Parse the results and create `app/src/main/assets/builds.json` — a map of species name → recommended build:
```json
{
  "garchomp": {
    "tier": "S",
    "notes": "Best physical sweeper, run Earthquake + Dragon Claw + Swords Dance",
    "recommendedMoves": ["Earthquake", "Dragon Claw", "Swords Dance", "Stone Edge"],
    "recommendedItem": "Life Orb"
  }
}
```

If Discord data is sparse for a species, omit it from the JSON (don't hallucinate).

**In the UI**, when a party slot is expanded:
- Show tier badge (S/A/B/C) if available
- Show recommended moves as a "suggested" row below actual moves
- Show recommended item if known

### 5. Enemy Party Scanning

Extend `AddressScanner.kt` to also locate `gEnemyParty`:
- It follows `gPlayerParty` in memory at a known offset
- Try: scan for a second valid party structure starting at `gPlayerParty + 624` (6 × 104 bytes)
- Store in `MainViewModel` as `enemyPartyState: StateFlow<List<PartyMon>>`

In the UI, show a compact enemy party row at the top (small icons + level only), and use enemy lead's stats/types for damage calculations.

## Move Data Reference

For the full move list, fetch and parse from:
`https://raw.githubusercontent.com/DepressoMocha/emerogue/master/src/data/moves_info.h`

This file has all move definitions. Parse `power`, `type`, `split` (physical/special/status) fields.

## Pokémon Species Data Reference

For base stats (needed for damage calc when party struct not yet decoded for enemy):
`https://raw.githubusercontent.com/DepressoMocha/emerogue/master/src/data/pokemon/base_stats/`

## Build Verification

After making changes, run:
```bash
cd /Users/sam.painter/Projects/er-companion && ./gradlew assembleDebug 2>&1
```

Fix any compilation errors before signaling completion.

## Completion Signal

When the build succeeds (APK produced at `app/build/outputs/apk/debug/app-debug.apk`), output the following text on its own line:

LOOP_COMPLETE
