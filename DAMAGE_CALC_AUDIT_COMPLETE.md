# Damage Calculator Audit - Complete

**Date**: 2026-03-26
**Auditor**: Claude Sonnet 4.5
**Scope**: Full audit of DamageCalculator.kt and all call sites

## Executive Summary

✅ **PASSED** - Damage calculator implements Gen 6+ mechanics correctly with one bug fixed.

### Bug Fixed
- **CompactBattleScreen.kt:1387-1398**: `calculateBestIncomingDamage` was missing `moveCategory` parameter
  - **Impact**: All special moves were calculated as physical (default moveCategory=0)
  - **Fix**: Added `moveCategory = md.category` parameter
  - **Status**: ✅ FIXED

## 1. moveCategory Parameter Verification

Checked all 11 production call sites for `DamageCalculator.calc()`:

### ✅ Correct Call Sites
1. **MainScreen.kt:415** - Party view best damage (player → enemy)
   - Passes `moveCategory = md.category` ✅
   - Stat selection: `if (md.category == 0) mon.attack else mon.spAttack` ✅

2. **MainScreen.kt:436** - Party view best damage (enemy → player)
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

3. **MainScreen.kt:1341** - Move item display
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

4. **MainScreen.kt:1433** - Move preview damage
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

5. **CompactBattleScreen.kt:831** - Best move selection
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

6. **CompactBattleScreen.kt:1084** - Compact move row display
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

7. **CompactBattleScreen.kt:1369** - Best outgoing damage helper
   - Passes `moveCategory = md.category` ✅
   - Stat selection: Correct ✅

8. **CompactBattleScreen.kt:1389** - Best incoming damage helper
   - Passes `moveCategory = md.category` ✅ (FIXED)
   - Stat selection: Correct ✅

9. **MainViewModel.kt:626** - ViewModel damage calculation
   - Passes `moveCategory = moveData.category` ✅
   - Comment: "REQUIRED: 0=Physical, 1=Special, 2=Status" ✅

10. **BattleAISimulator.kt:79** - AI move evaluation
    - Passes `moveCategory = moveData.category` ✅
    - Stat selection: Correct ✅

11. **MoveSimulator.kt:415** - Battle simulation
    - Passes `moveCategory = moveData.category` ✅
    - Stat selection: Uses pre-calculated stats ✅

## 2. Type Chart Verification (Gen 6+ Compliance)

Verified 18×18 type effectiveness table in `DamageCalculator.kt:43-80`:

### Steel Type Changes (Gen 6+)
- ✅ Ghost → Steel: 1.0x (was 0.5x in Gen 2-5)
- ✅ Dark → Steel: 1.0x (was 0.5x in Gen 2-5)
- ✅ Steel → Ghost: 1.0x
- ✅ Steel → Dark: 1.0x

### Fairy Type (Gen 6+)
- ✅ Dragon → Fairy: 0.0x (immune)
- ✅ Fairy → Fighting: 2.0x (super effective)
- ✅ Fairy → Dragon: 2.0x (super effective)
- ✅ Fairy → Dark: 2.0x (super effective)
- ✅ Poison → Fairy: 2.0x (super effective)
- ✅ Steel → Fairy: 2.0x (super effective)
- ✅ Fairy → Bug: 0.5x (resisted)

**Result**: Type chart is fully Gen 6+ compliant ✅

## 3. Burn Mechanics (Gen 6+)

Verified in `DamageCalculator.kt:335`:

```kotlin
if (isBurned && moveCategory == 0 && attackerAbility != 62) {
    baseDamage = baseDamage / 2
}
```

- ✅ Only affects physical moves (`moveCategory == 0`)
- ✅ Applied BEFORE +2 constant (Gen 3+ order)
- ✅ Guts ability (ID 62) negates burn penalty
- ✅ Gen 6+ mechanic: burn does NOT affect special moves

**Result**: Burn mechanics correct for Gen 6+ ✅

## 4. Critical Hit Multiplier

Critical hits are **not implemented** in base damage calculator (noted in code comments line 99).
- This is acceptable as crits are typically handled at simulation level
- If needed, Gen 6+ crit multiplier is 1.5x (not 2.0x)

## 5. STAB Mechanics

Verified in `DamageCalculator.kt:344-370`:

- ✅ Base STAB: 1.5x (`baseDamage * 15 / 10`)
- ✅ Adaptability: 2.0x (`baseDamage * 2`)
- ✅ Applied AFTER burn, BEFORE type effectiveness (correct order)
- ✅ Uses integer math to match GBA code

**Result**: STAB mechanics correct ✅

## 6. Weather Multipliers

Verified in `WeatherEffects.kt:54-95`:

### Sun / Harsh Sun
- ✅ Fire moves: 1.5x boost
- ✅ Water moves: 0.5x reduction

### Rain / Heavy Rain
- ✅ Water moves: 1.5x boost
- ✅ Fire moves: 0.5x reduction

### Sandstorm
- ✅ No direct damage multiplier
- ✅ Rock-type SpDef: 1.5x boost (line 149)

### Other Weather
- Hail: No damage multiplier ✅
- Strong Winds: Special case for Flying defense ✅

**Result**: Weather multipliers correct for Gen 6+ ✅

## 7. Ability Effects - No Double-Application

Verified in `AbilityEffects.kt:317-359`:

### `getAttackerStatMultiplier` (lines 317-332)
- ✅ Only applies **status-dependent** abilities (Guts: 1.5x Attack when statused)
- ✅ Explicitly excludes Huge Power/Pure Power (already in gBattleMons stats)
- ✅ Comment confirms: "already in the stats read from memory"

### `getDefenderStatMultiplier` (lines 342-359)
- ✅ Marvel Scale: 1.5x Defense when statused (status-dependent)
- ✅ Fur Coat: 2.0x Defense always

### Intimidate
- ✅ NOT re-applied in damage calc
- ✅ Already reflected in gBattleMons[slot].attack from memory
- ✅ Applied only once on switch-in (AbilityEffects.kt:70-82)

**Result**: No double-application of stat effects ✅

## 8. Formula Verification

Gen 6+ damage formula (matches code implementation):

```
baseDamage = floor((2*level/5+2) * power * atk/def / 50)
if (burned && physical && !guts): baseDamage = baseDamage / 2
baseDamage += 2
if (STAB): baseDamage *= 1.5 (or 2.0 for Adaptability)
damage = baseDamage * type_effectiveness
damage *= weather_multiplier
damage *= terrain_multiplier
damage *= ability_multipliers
damage *= item_multipliers
finalDamage = damage * random(0.85, 1.0)
```

**Order of operations** (DamageCalculator.kt:320-469):
1. Base damage calculation ✅
2. Burn reduction (physical only, before +2) ✅
3. Add +2 constant ✅
4. Apply STAB ✅
5. Apply type effectiveness ✅
6. Apply weather multiplier ✅
7. Apply terrain multiplier ✅
8. Apply ability multipliers ✅
9. Apply item multipliers ✅
10. Apply random factor (85-100%) ✅

**Result**: Formula order correct for Gen 6+ ✅

## 9. Test Calculations

### Test Case 1: Blaziken vs Milotic
- Lv50 Blaziken (Atk 120, Fire/Fighting)
- Move: Blaze Kick (Power 85, Fire, Physical)
- vs Lv50 Milotic (Def 79, Water)

Calculation:
```
levelMod = (2*50/5+2) = 22
baseDamage = (85 * 120 * 22) / (79 * 50) = 224400 / 3950 = 56
baseDamage += 2 = 58
STAB (Fire on Fire/Fighting): 58 * 1.5 = 87
Type (Fire → Water): 87 * 0.5 = 43
Random: min = 43 * 0.85 = 36, max = 43
```
**Expected**: 36-43 damage (21.5-25.7% of Milotic's ~167 HP)

### Test Case 2: Gardevoir vs Machamp
- Lv50 Gardevoir (SpAtk 125, Psychic/Fairy)
- Move: Psychic (Power 90, Psychic, Special)
- vs Lv50 Machamp (SpDef 60, Fighting)

Calculation:
```
levelMod = 22
baseDamage = (90 * 125 * 22) / (60 * 50) = 247500 / 3000 = 82
baseDamage += 2 = 84
STAB (Psychic on Psychic/Fairy): 84 * 1.5 = 126
Type (Psychic → Fighting): 126 * 2.0 = 252
Random: min = 252 * 0.85 = 214, max = 252
```
**Expected**: 214-252 damage (148-175% of Machamp's ~145 HP → OHKO)

## 10. Stat Selection at Call Sites

All call sites correctly select stats based on move category:

```kotlin
attackStat = if (moveData.category == 0) mon.attack else mon.spAttack
defenseStat = if (moveData.category == 0) defender.defense else defender.spDefense
```

- Physical (category=0): Uses Attack vs Defense ✅
- Special (category=1): Uses SpAttack vs SpDefense ✅

## Summary of Findings

| Check | Status | Notes |
|-------|--------|-------|
| moveCategory passed at all call sites | ✅ PASS | 1 bug fixed |
| Stat selection (Atk/SpAtk, Def/SpDef) | ✅ PASS | All correct |
| Type chart Gen 6+ compliance | ✅ PASS | Steel/Fairy correct |
| Burn halves physical damage only | ✅ PASS | Gen 6+ mechanic |
| STAB: 1.5x base, 2.0x Adaptability | ✅ PASS | Correct values |
| Weather multipliers | ✅ PASS | Sun/Rain correct |
| No double-application of abilities | ✅ PASS | Guts/Intimidate correct |
| Formula order of operations | ✅ PASS | Matches Gen 6+ |
| Critical hits (x1.5) | ⚠️ N/A | Not implemented (acceptable) |

## Conclusion

The damage calculator correctly implements **Gen 6+ mechanics**:
- Physical/Special split (Gen 4+)
- Burn affects physical only (Gen 6+)
- Type chart includes Fairy, Steel resistances updated
- STAB, weather, ability effects all correct
- No double-application of stat modifiers

**One bug fixed**: `CompactBattleScreen.kt` missing `moveCategory` parameter.

**Build verification**: Next step is to run `./gradlew assembleDebug` to verify compilation.
