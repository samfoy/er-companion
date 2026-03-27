# Task: Audit & Verify Damage Calculations in ER Companion

Read and audit `app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`.

## IMPORTANT: emerogue uses MODERN (Gen 6+) mechanics

Emerald Rogue is built on pokeemerald but uses modern battle mechanics via compile-time flags (B_UPDATED_*, GEN_LATEST in include/config/battle.h). Do NOT assume Gen 3 behavior. Key rules:

- **Physical/Special split**: Gen 4+ per-move split — each move has its own category (not Gen 3 type-based)
- **Burn**: halves physical attack damage only (Gen 6+)
- **Critical hits**: x1.5 (Gen 6+, NOT x2)
- **STAB**: x1.5
- **Type chart**: Gen 6+ with Fairy type — Steel no longer resists Ghost/Dark
- **Damage formula**: standard modern formula — float-equivalent is fine

Check the emerogue source https://github.com/DepressoMocha/emerogue include/config/battle.h to confirm which gen is active.

## What to Audit

### 1. moveCategory passed to ALL callers
Search every call to `DamageCalculator.calc()` in the codebase:
- `app/src/main/java/com/ercompanion/ui/MainScreen.kt`
- `app/src/main/java/com/ercompanion/ui/CompactBattleScreen.kt`
- `app/src/main/java/com/ercompanion/MainViewModel.kt`
- `app/src/main/java/com/ercompanion/calc/BattleAISimulator.kt`
- `app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`

Every caller MUST pass `moveCategory = moveData.category` (0=Physical, 1=Special). Without it, the parameter defaults to 0 (Physical) for all moves — which breaks special moves.

### 2. Stat selection at call sites
Each caller must select:
- `attackStat = if (moveData.category == 0) mon.attack else mon.spAttack`
- `defenseStat = if (moveData.category == 0) defender.defense else defender.spDefense`
Fix any that don't.

### 3. No double-application of gBattleMons stats
The app reads live stats from gBattleMons which already include Intimidate and stat stage changes. `AbilityEffects.getAttackerStatMultiplier` should NOT re-apply Intimidate or Huge Power. Check it only applies conditional things like Guts (status-based).

### 4. Weather multipliers
Sun: Fire x1.5, Water x0.5. Rain: Water x1.5, Fire x0.5. Check these are correct in WeatherEffects.kt.

### 5. Type chart completeness
Verify the 18x18 type chart in DamageCalculator.kt matches Gen 6+:
- Steel does NOT resist Ghost or Dark
- Fairy is immune to Dragon, resists Fighting/Bug/Dark, weak to Poison/Steel

### 6. Test with concrete numbers
Use the formula: `floor((floor((2*level/5+2) * power * atk/def / 50) + 2) * stab * type * random/100)`

Test case 1: Lv50 Blaziken (Atk 120) uses Blaze Kick (Power 85, Fire/Physical) vs Lv50 Milotic (Def 79)
- No STAB (Blaziken is Fire/Fighting, Blaze Kick is Fire — STAB applies!)
- Type effectiveness: Fire vs Water = 0.5x
- Expected ~28-33 damage (neutral would be ~56-66, halved for resistance)

Test case 2: Lv50 Gardevoir (SpAtk 125) uses Psychic (Power 90, Special) vs Lv50 Machamp (SpDef 60)
- STAB (Gardevoir is Psychic)
- Type effectiveness: Psychic vs Fighting = 2x
- Expected ~140-165 damage

Calculate manually and verify the code produces the same output (within 1-2 due to rounding).

## Fix Everything Found
Make minimal surgical fixes. Add comments where helpful.

## Build & Verify
Run: `./gradlew assembleDebug`
Must compile clean.

Commit with: "fix: damage calc audit — moveCategory at all callers, Gen 6+ mechanics verified"
Then run: openclaw system event --text "Done: Damage calc audit complete" --mode now
