# Emerald Rogue Battle Mechanics Reference

**Date:** 2026-03-26
**Source:** `/Users/samfp/emerogue/include/config/battle.h`

## Executive Summary

**Emerald Rogue uses GEN_LATEST (Gen 8/9) mechanics for almost all battle calculations, NOT Gen 3 mechanics.**

This is CRITICAL for our damage calculator - many of our recent "fixes" assuming Gen 3 mechanics were **incorrect** and may have introduced bugs.

---

## Key Mechanics Differences

### ✅ PARALYSIS (Issue 2.5 - Our fix was CORRECT)
- **Config:** `B_PARALYSIS_SPEED GEN_LATEST` (Line 7)
- **Code:** `/Users/samfp/emerogue/src/battle_main.c` - `speed /= B_PARALYSIS_SPEED >= GEN_7 ? 2 : 4;`
- **Gen 7+ (ER uses):** Speed ÷ 2 = **50% remaining speed**
- **Gen 3-6 (OLD):** Speed ÷ 4 = **25% remaining speed**
- **Our implementation:** Changed from 0.25x to 0.5x ✅ **CORRECT**

### ⚠️ BURN DAMAGE (Need to verify)
- **Config:** `B_BURN_DAMAGE GEN_LATEST` (Line 28)
- **Gen 7+ (ER uses):** 1/16 max HP per turn
- **Gen 3-6:** 1/8 max HP per turn
- **Our implementation:** Need to check what we use

### ✅ TYPE CHART (Fairy Type)
- **Confirmed:** Emerald Rogue has **18 types** including Fairy (TYPE_FAIRY used throughout)
- **Our DamageCalculator:** Uses 18x18 type chart ✅ **CORRECT**

### ✅ CRITICAL HIT MULTIPLIER
- **Config:** `B_CRIT_MULTIPLIER GEN_LATEST` (Line 6)
- **Gen 6+ (ER uses):** 1.5x damage
- **Gen 3-5:** 2.0x damage
- **Action needed:** Verify our DamageCalculator uses 1.5x not 2x

### ✅ PHYSICAL/SPECIAL SPLIT
- **Config:** `B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST` (Line 67)
- **Gen 4+:** Moves have individual physical/special categorization
- **Gen 3:** Type-based (Fire/Water/Electric = Special, Normal/Fighting = Physical)
- **Our implementation:** Already using move-specific categories ✅ **CORRECT**

### ⚠️ SANDSTORM SP.DEF BOOST
- **Config:** `B_SANDSTORM_SPDEF_BOOST GEN_LATEST` (Line 12)
- **Gen 4+ (ER uses):** Rock-types get 1.5x Sp. Def in Sandstorm
- **Gen 3:** No boost
- **Action needed:** Verify WeatherEffects.kt implements this

### ✅ BADGE BOOSTS
- **Config:** `B_BADGE_BOOST GEN_LATEST` (Line 22)
- **Gen 4+ (ER uses):** Gym Badges do NOT boost stats
- **Gen 3:** Badges boost stats in battle
- **Our implementation:** Don't need to implement badge boosts ✅ **CORRECT**

### ⚠️ CONFUSION SELF-DAMAGE
- **Config:** `B_CONFUSION_SELF_DMG_CHANCE GEN_LATEST` (Line 8)
- **Gen 7+ (ER uses):** 33.3% chance of self-damage
- **Gen 3-6:** 50% chance
- **Action needed:** Check if we implement confusion

### ⚠️ STATUS IMMUNITY
- **Config:** `B_PARALYZE_ELECTRIC GEN_LATEST` (Line 43)
- **Gen 6+:** Electric-types immune to paralysis
- **Config:** `B_POWDER_GRASS GEN_LATEST` (Line 44)
- **Gen 6+:** Grass-types immune to powder moves
- **Action needed:** Verify these immunities in our status move code

---

## What This Means for Our Code

### ✅ Fixes That Were CORRECT (Gen 7-9 Mechanics)
1. **Paralysis 50% speed reduction** (Issue 2.5) ✅
2. **Fairy type in type chart** ✅
3. **Physical/special split** ✅
4. **Modern ability implementations** ✅
5. **Weather/terrain systems** ✅

### ⚠️ Fixes That May Be WRONG (Assumed Gen 3)
1. **Speed tie handling** (Issue 2.2) - Need to verify Gen 7+ behavior
2. **Sleep mechanics** (Issue 2.6) - Gen 5+ uses 1-3 turns, not 2-5
3. **Toxic counter on switch** (Issue 2.3) - Gen 7+ behavior may differ
4. **End-of-turn effect order** (Issue 2.7) - Gen 7+ order may be different

### 🔍 Need to Verify
1. **Burn damage:** Should be 1/16 not 1/8
2. **Crit multiplier:** Should be 1.5x not 2x
3. **Sandstorm Sp.Def boost:** Rock-types get 1.5x Sp.Def
4. **Confusion:** 33.3% self-damage chance
5. **Type immunities:** Electric immune to paralysis, Grass immune to powder

---

## Recommendations

### Immediate Actions
1. ✅ **Keep paralysis fix** (0.5x speed) - This was correct for Gen 7+
2. 🔍 **Audit DamageCalculator.kt** for Gen 3 vs Gen 7-9 differences
3. 🔍 **Check MoveSimulator.kt** status mechanics against Gen 7+ rules
4. 🔍 **Verify WeatherEffects.kt** includes Sandstorm Sp.Def boost

### Testing Strategy
For each mechanic, we should:
1. Check what generation introduced the change
2. Verify config setting in `/Users/samfp/emerogue/include/config/battle.h`
3. Find implementation in emerogue source (`/Users/samfp/emerogue/src/`)
4. Match our implementation to emerogue's behavior

### Source of Truth
- **Primary:** `/Users/samfp/emerogue/include/config/battle.h` - Shows which gen each mechanic uses
- **Implementation:** `/Users/samfp/emerogue/src/battle_*.c` - Shows actual code
- **Type chart:** `/Users/samfp/emerogue/src/battle_util.c` around line 1216

---

## Quick Reference: GEN_LATEST = Gen 8/9

Almost every config uses `GEN_LATEST` which means **the most recent generation mechanics (Gen 8/9)**, not Gen 3. Only a few settings use `GEN_3`:
- `B_WHITEOUT_MONEY GEN_3` (Line 10) - Money loss on defeat
- `B_EXP_CATCH GEN_3` (Line 15) - No exp from catching
- `B_X_ITEMS_BUFF GEN_3` (Line 147) - X Items boost by 1 stage not 2
- `B_EVOLUTION_AFTER_WHITEOUT GEN_3` (Line 226) - No evolution after loss

Everything else battle-related uses **modern mechanics**.
