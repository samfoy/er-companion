# Research Task: Fix Damage Calculations in ER Companion

You are a deep research agent. Your job is to audit and fix the damage calculation system in the ER Companion app to make it accurate for Pokémon Emerald Rogue (emerogue).

## Repo Context
- Project: ~/Projects/er-companion
- Game: Pokémon Emerald Rogue — GBA romhack based on Gen 3 Emerald, with Gen 4-9 moves added
- Start by reading: app/src/main/java/com/ercompanion/calc/DamageCalculator.kt

## What to Research

### 1. Gen 3 Damage Formula (Ground Truth)
Reference sources:
- https://github.com/pret/pokeemerald (look at src/battle_util.c and src/battle_script_commands.c)
- https://bulbapedia.bulbagarden.net/wiki/Damage#Generation_III
- Gen 3 formula: ((2*Level/5 + 2) * Power * Atk/Def) / 50 + 2 with modifiers

Key Gen 3 quirks to verify:
- STAB: x1.5 (integer: x150 / 100)
- Critical hit: x2 in Gen 3 (NOT x1.5 like later gens)
- Weather: x1.5/0.5 for sun/rain on fire/water moves
- Type effectiveness: Gen 3 type chart (check Steel immune to Poison, Dark/Ghost interactions)
- Burn: halves physical attack
- Move category: Gen 3 uses physical/special split BY TYPE — but emerogue likely uses Gen 4+ per-move split

### 2. emerogue Move Categories
- Does emerogue use Gen 3 type-based split or Gen 4+ physical/special per-move?
- Check https://github.com/DepressoMocha/emerogue — look for move definitions and FLAG_PHYSICAL_MOVE or similar
- This is critical — wrong split means every physical/special assignment is wrong

### 3. What the Current Calculator Gets Wrong
After reading DamageCalculator.kt:
- Does it use the right formula?
- Does it handle the physical/special split correctly?
- Does it apply STAB correctly?
- Is the type effectiveness table complete and correct for Gen 3?
- Are there integer rounding errors vs the GBA formula?

### 4. gBattleMons Stat Integration
- The app reads live battle stats from gBattleMons (EWRAM+0x1C358) — these are post-stat-stage stats
- Damage calc should use THESE modified stats, not base stats from the party struct
- Verify that MainViewModel.kt passes gBattleMons stats into DamageCalculator correctly
- If not, fix the integration

### 5. Ability Modifiers
- Some abilities affect damage: Hustle, Huge Power, Guts, Flash Fire, Levitate (type immunity), Intimidate, etc.
- Check AbilityData.kt — are ability effects wired into the damage calc?
- Note which abilities affect offense/defense and whether we account for them

## Implementation
1. Fix the damage formula in DamageCalculator.kt to be accurate
2. Fix the physical/special split if wrong
3. Fix the type effectiveness table if incomplete
4. Ensure gBattleMons live stats are used for active battlers
5. Add comments citing the source for each formula step

Commit with: "fix: accurate Gen3/emerogue damage calculation formula and type chart"
Then run: openclaw system event --text "Done: Damage calc research + fixes committed to er-companion" --mode now
