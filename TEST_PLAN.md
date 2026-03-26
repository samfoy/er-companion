# Battle Calculator Test Plan for Emerald Rogue

## Overview
This document provides a comprehensive test plan to verify that the ER Companion battle calculator accurately matches Emerald Rogue's actual battle mechanics. Emerald Rogue uses Gen 7-9 mechanics with some modifications.

**Last Updated:** 2026-03-26

---

## 1. Current Test Coverage Summary

### 1.1 Existing Test Files
- `DamageCalculatorTest.kt` (1,016 lines) - Core damage calculation
- `AbilityEffectsTest.kt` (405 lines) - Ability interactions
- `WeatherEffectsTest.kt` (405 lines) - Weather and terrain
- `ItemEffectsTest.kt` (429 lines) - Held item effects
- `MoveSimulatorTest.kt` (446 lines) - Status conditions and move simulation
- `OptimalLineCalculatorTest.kt` (600 lines) - Strategic calculations
- `DeepAnalysisModeTest.kt` (368 lines) - Advanced analysis

### 1.2 Well-Covered Mechanics
✅ **Damage Formula**
- Base damage calculation (levels, stats, move power)
- Random roll range (85-100%)
- Minimum damage of 1
- STAB multiplier (1.5x normal, 2.0x with Adaptability)
- Type effectiveness (including Fairy type)
- Critical hits (1.5x in Gen 6+)

✅ **Type System**
- All 18 Gen 6+ types including Fairy
- Type chart validation (18x18 matrix)
- Dual-type effectiveness calculations
- All immunities (Normal→Ghost, Electric→Ground, Dragon→Fairy, etc.)
- Type effectiveness caching for performance

✅ **Weather Effects**
- Sun: 1.5x Fire moves, 0.5x Water moves
- Rain: 1.5x Water moves, 0.5x Fire moves
- Sandstorm: 1/16 HP damage, Rock type Sp.Def 1.5x boost
- Hail: 1/16 HP damage to non-Ice types
- Primal weather (Harsh Sun, Heavy Rain, Strong Winds) cannot be changed

✅ **Terrain Effects**
- Electric Terrain: 1.5x Electric moves (grounded only)
- Grassy Terrain: 1.5x Grass moves, 1/16 HP healing (grounded only)
- Misty Terrain: 0.5x Dragon moves (grounded only)
- Psychic Terrain: 1.5x Psychic moves (grounded only)
- Flying types unaffected by terrain

✅ **Abilities**
- Intimidate: -1 Attack on switch-in
- Moxie: +1 Attack on KO
- Speed Boost: +1 Speed at end of turn
- Guts: 1.5x Attack when statused (ignores burn penalty)
- Marvel Scale: 1.5x Defense when statused
- Technician: 1.5x for moves ≤60 BP
- Weather-setting abilities (Drought, Drizzle, Sand Stream, Snow Warning)
- Primal weather abilities (Desolate Land, Primordial Sea, Delta Stream)
- Weather speed abilities (Chlorophyll, Swift Swim, Sand Rush, Slush Rush)

✅ **Held Items**
- Choice items (Band/Specs/Scarf): 1.5x stat or speed
- Life Orb: 1.3x damage, 10% recoil
- Expert Belt: 1.2x on super effective
- Muscle Band/Wise Glasses: 1.1x for physical/special
- Leftovers: 1/16 HP healing per turn
- Focus Sash: Survive at 1 HP when full
- Black Sludge: 1/16 HP healing for Poison types, 1/8 damage for others
- Status orbs (Flame Orb, Toxic Orb)

✅ **Status Conditions**
- Burn: 1/16 HP damage, 0.5x physical damage (unless Guts)
- Paralysis: 50% speed reduction (Gen 7+), 25% fail chance
- Poison: 1/8 HP damage per turn
- Toxic: Increasing damage (1/16, 2/16, 3/16...)
- Sleep: 1-3 turn duration, prevents moves
- Freeze: Prevents moves until thawed

✅ **Stat Stages**
- All 13 multiplier values (-6 to +6)
- Clamping to valid range
- Stat stage changes from moves (Swords Dance, Dragon Dance, etc.)

---

## 2. Mechanics Requiring Additional Testing

### 2.1 Critical Priority - Core Battle Mechanics

#### 2.1.1 Gen 7+ Critical Hit Changes
**Status:** ⚠️ Test exists but needs validation
**Current Implementation:** 1.5x multiplier in Gen 6+
**Emerald Rogue Behavior:** Uses Gen 7 mechanics
- Critical hit ratio stages (1:24, 1:8, 1:2, 100%)
- Moves like Frost Breath, Storm Throw always crit
- Focus Energy, Dire Hit, and crit-boosting items
- Scope Lens and Razor Claw effects

**Test Cases Needed:**
```kotlin
@Test
fun testCriticalHitGen7Mechanics() {
    // Test base crit rate (1/24)
    // Test with Focus Energy (+2 stages → 50% crit rate)
    // Test with Scope Lens (+1 stage)
    // Test high crit moves (Slash, Stone Edge → +1 stage)
    // Test always-crit moves (Frost Breath)
}

@Test
fun testCriticalHitIgnoresStatStages() {
    // Crits should ignore negative attack stages
    // Crits should ignore positive defense stages
    // Test with -6 Attack and crit vs no crit
}
```

#### 2.1.2 Paralysis Speed Reduction
**Status:** ✅ Tested (50% speed reduction)
**Emerald Rogue:** Confirmed Gen 7+ (50% instead of Gen 6's 75%)
**Needs:** Cross-validation with in-game tests

#### 2.1.3 Burn Physical Damage Reduction
**Status:** ✅ Tested (0.5x damage)
**Emerald Rogue:** Confirmed (1/16 HP damage per turn, 0.5x physical)
**Needs:** Verify Guts interaction is correct

### 2.2 High Priority - Advanced Mechanics

#### 2.2.1 Multi-Hit Moves
**Status:** ❌ Not covered
**Emerald Rogue:** Should use Gen 7+ mechanics

**Test Cases Needed:**
```kotlin
@Test
fun testMultiHitMoveDamage() {
    // Bullet Seed, Rock Blast, etc.
    // 2 hits: 35% chance
    // 3 hits: 35% chance
    // 4 hits: 15% chance
    // 5 hits: 15% chance
    // Each hit rolls damage separately
}

@Test
fun testParentalBondAbility() {
    // Gen 7+: Second hit is 25% of first
    // Gen 6: Second hit is 50% of first
    // Which does Emerald Rogue use?
}

@Test
fun testSkillLinkAbility() {
    // Multi-hit moves always hit 5 times
}
```

#### 2.2.2 Recoil Moves
**Status:** ⚠️ Partially covered (Life Orb recoil)
**Emerald Rogue:** Needs validation

**Test Cases Needed:**
```kotlin
@Test
fun testRecoilDamage() {
    // Double-Edge, Brave Bird: 1/3 recoil
    // Take Down, Submission: 1/4 recoil
    // Wild Charge, Wood Hammer: 1/4 recoil
    // Head Smash: 1/2 recoil
}

@Test
fun testRockHeadAbility() {
    // No recoil damage from recoil moves
}
```

#### 2.2.3 Priority Moves
**Status:** ❌ Not covered
**Emerald Rogue:** Standard priority system

**Test Cases Needed:**
```kotlin
@Test
fun testPriorityBrackets() {
    // +5: Helping Hand
    // +4: Protect, Detect
    // +3: Fake Out, Extreme Speed
    // +2: None standard
    // +1: Quick Attack, Aqua Jet, Mach Punch
    // 0: Normal moves
    // -1 to -7: Trick Room priority
}

@Test
fun testPriorityWithPrankster() {
    // Prankster: +1 priority to status moves
    // Dark types immune in Gen 7+
}
```

#### 2.2.4 Z-Moves
**Status:** ❌ Not covered
**Emerald Rogue:** May or may not include Z-Moves

**Investigation Needed:**
- Are Z-Moves available in Emerald Rogue?
- If yes, test Z-Move base power calculation
- Test Z-Status move effects

#### 2.2.5 Mega Evolution
**Status:** ❌ Not covered
**Emerald Rogue:** Likely includes Mega Evolution

**Test Cases Needed:**
```kotlin
@Test
fun testMegaEvolutionStatChanges() {
    // Verify stats change correctly
    // Verify ability changes
    // Verify type changes (Mega Charizard X)
}

@Test
fun testMegaEvolutionSpeed() {
    // In Gen 7+, Mega speed applies same turn
    // Test turn order with Mega Evolution
}
```

### 2.3 Medium Priority - Edge Cases

#### 2.3.1 Abilities with Complex Interactions
**Status:** ⚠️ Basic tests exist

**Additional Test Cases:**
```kotlin
@Test
fun testMoldBreakerAbility() {
    // Ignores abilities like Levitate, Wonder Guard
    // Test with multiple target abilities
}

@Test
fun testWonderGuardAbility() {
    // Only super effective moves hit
    // Test with different coverage moves
}

@Test
fun testSturdyAbility() {
    // Gen 5+: Survives at 1 HP when full HP
    // Test vs OHKO moves
}

@Test
fun testMultiscaleAbility() {
    // 0.5x damage when at full HP
    // Test with Dragonite
}

@Test
fun testDisguiseAbility() {
    // Gen 7+: Takes 0 damage from first hit
    // Takes 1/8 recoil damage
}
```

#### 2.3.2 Weather-Dependent Forms
**Status:** ❌ Not covered

**Test Cases Needed:**
```kotlin
@Test
fun testCastformWeatherForms() {
    // Sunny Form in sun (Fire type)
    // Rainy Form in rain (Water type)
    // Snowy Form in hail (Ice type)
    // Normal Form otherwise
}

@Test
fun testCherrimSunnyForm() {
    // Attack and Sp.Def increase in sun
}
```

#### 2.3.3 Accuracy and Evasion
**Status:** ❌ Not covered

**Test Cases Needed:**
```kotlin
@Test
fun testAccuracyStages() {
    // Test all stages from -6 to +6
    // Base accuracy modifications
}

@Test
fun testEvasionStages() {
    // Test with Double Team, Minimize
    // Interaction with accuracy stages
}

@Test
fun testNoGuardAbility() {
    // All moves hit regardless of accuracy
}
```

### 2.4 Low Priority - Rare Mechanics

#### 2.4.1 Fixed Damage Moves
**Status:** ❌ Not covered

**Test Cases Needed:**
```kotlin
@Test
fun testFixedDamageMoves() {
    // Dragon Rage: Always 40 damage
    // Sonic Boom: Always 20 damage
    // Night Shade/Seismic Toss: Damage = user level
}

@Test
fun testPsywave() {
    // Random damage: 0.5x to 1.5x user level
}
```

#### 2.4.2 OHKO Moves
**Status:** ❌ Not covered

**Test Cases Needed:**
```kotlin
@Test
fun testOHKOMoves() {
    // Fissure, Guillotine, Horn Drill, Sheer Cold
    // Accuracy = 30% + (attacker level - defender level)
    // Fails if defender level > attacker level
}

@Test
fun testSturdyBlocksOHKO() {
    // Sturdy should block OHKO moves
}
```

#### 2.4.3 Two-Turn Moves
**Status:** ❌ Not covered

**Test Cases Needed:**
```kotlin
@Test
fun testTwoTurnMoves() {
    // Fly, Dig, Dive: Invulnerable on turn 1
    // Solar Beam: Instant in sun
    // Skull Bash, Sky Attack, Razor Wind
}

@Test
fun testPowerHerbItem() {
    // Skips charging turn for two-turn moves
}
```

---

## 3. Test Implementation Strategy

### 3.1 Phase 1: Critical Mechanics (Week 1)
1. **Critical Hit System**
   - Implement stage-based crit rate
   - Test always-crit moves
   - Verify stat stage interaction

2. **Multi-Hit Moves**
   - Implement hit count distribution
   - Test with various abilities (Skill Link, Parental Bond)

3. **Recoil Moves**
   - Verify recoil calculation
   - Test Rock Head ability

### 3.2 Phase 2: Advanced Mechanics (Week 2)
1. **Priority System**
   - Implement priority brackets
   - Test with Prankster and other priority abilities

2. **Complex Abilities**
   - Mold Breaker, Wonder Guard
   - Multiscale, Disguise
   - Weather-dependent abilities

3. **Accuracy/Evasion**
   - Implement stage calculations
   - Test with abilities like No Guard

### 3.3 Phase 3: Edge Cases (Week 3)
1. **Special Damage Types**
   - Fixed damage moves
   - OHKO moves
   - Two-turn moves

2. **Form Changes**
   - Castform, Cherrim
   - Mega Evolution (if applicable)

3. **Rare Interactions**
   - Weather + Terrain + Ability stacking
   - Multiple stat stage changes in one turn

### 3.4 Phase 4: Integration Testing (Week 4)
1. **Real Battle Scenarios**
   - Record actual Emerald Rogue battles
   - Compare calculator output to in-game results
   - Document any discrepancies

2. **Performance Testing**
   - Ensure calculations complete in <100ms
   - Test with extreme stat values
   - Verify no integer overflow

---

## 4. In-Game Validation Protocol

### 4.1 Setup
1. Use Emerald Rogue's damage calculator (if available)
2. Set up controlled battles with known Pokemon/moves
3. Record actual damage values

### 4.2 Test Scenarios

#### Scenario 1: Basic Damage
```
Pokemon: Charizard Level 50
Move: Flamethrower (90 BP)
Target: Venusaur Level 50
Expected: [Calculate with our system]
Actual: [Record in-game]
```

#### Scenario 2: Weather Effects
```
Pokemon: Kingdra Level 50
Move: Hydro Pump (110 BP)
Weather: Rain
Target: Tyranitar Level 50
Expected: [Calculate]
Actual: [Record]
```

#### Scenario 3: Ability Interactions
```
Pokemon: Heracross Level 50 (Guts)
Status: Burned
Move: Close Combat (120 BP)
Target: Blissey Level 50
Expected: No burn penalty, 1.5x from Guts
Actual: [Record]
```

#### Scenario 4: Complex Stacking
```
Pokemon: Venusaur Level 50 (Chlorophyll)
Move: Solar Beam (120 BP)
Weather: Sun
Terrain: Grassy
Item: Life Orb
Target: Swampert Level 50
Expected: STAB(1.5) * Sun(1.5) * Grassy(1.5) * Life Orb(1.3)
Actual: [Record]
```

### 4.3 Discrepancy Resolution
When actual != expected:
1. Verify Pokemon stats (EVs, IVs, nature)
2. Check for hidden abilities or items
3. Review generation-specific mechanics
4. Document and update implementation
5. Add regression test

---

## 5. Automated Test Suite Structure

### 5.1 Unit Tests (Fast, <1s total)
- Individual formula components
- Type chart lookups
- Stat stage multipliers
- Basic ability effects

### 5.2 Integration Tests (Medium, <10s total)
- Full damage calculations
- Multi-effect stacking
- Weather + terrain + ability
- Status condition interactions

### 5.3 Validation Tests (Slow, <60s total)
- Compare against known battle logs
- Test all Pokemon combinations
- Exhaustive move coverage
- Performance benchmarks

### 5.4 Continuous Integration
```yaml
# Run on every commit
- Unit tests
- Fast integration tests

# Run on PR merge
- Full integration tests
- Validation tests

# Run nightly
- Performance benchmarks
- Coverage analysis
- Memory profiling
```

---

## 6. Documentation Requirements

### 6.1 Per Test File
- Clear test names describing what's tested
- Comments explaining expected behavior
- References to Bulbapedia or official sources
- Emerald Rogue-specific notes

### 6.2 Test Data
- `test-data/known-calculations.json` - Verified damage values
- `test-data/battle-logs.txt` - Real battle recordings
- `test-data/edge-cases.json` - Special scenarios

### 6.3 Coverage Reports
- Maintain >90% code coverage
- Track coverage per feature
- Identify untested code paths

---

## 7. Known Limitations and Assumptions

### 7.1 Current Assumptions
1. **Generation:** Emerald Rogue uses Gen 7-9 mechanics
2. **Damage Formula:** Standard Gen 7+ formula
3. **Status Duration:** Sleep is 1-3 turns (Gen 7+)
4. **Paralysis:** 50% speed reduction (Gen 7+)
5. **Burn:** 1/16 HP per turn (Gen 7+)
6. **Terrain:** 1.5x boost in Gen 7, reduced to 1.3x in Gen 8+
   - **Action Required:** Verify which Emerald Rogue uses

### 7.2 Features Requiring Investigation
- [ ] Does Emerald Rogue include Z-Moves?
- [ ] Does Emerald Rogue include Dynamax/Gigantamax?
- [ ] Which Mega Evolutions are available?
- [ ] Are there any custom abilities or moves?
- [ ] Does Parental Bond use Gen 6 (50%) or Gen 7+ (25%) multiplier?
- [ ] Terrain boost: 1.5x (Gen 7) or 1.3x (Gen 8+)?

### 7.3 Mechanics Definitely Not Included
- Gen 9 Terastallization (too new)
- Most Gen 9 moves and abilities
- Gen 9 type chart changes (if any)

---

## 8. Success Criteria

### 8.1 Test Coverage Goals
- ✅ 100% of core damage formula
- ✅ 100% of type effectiveness
- ⚠️ 90% of abilities (currently ~70%)
- ⚠️ 90% of held items (currently ~80%)
- ❌ 80% of move special effects (currently ~40%)
- ❌ 100% of status conditions (currently ~70%)
- ⚠️ 100% of weather effects (currently 90%)
- ⚠️ 100% of terrain effects (currently 90%)

### 8.2 Accuracy Goals
- <1% error on standard damage calculations
- <2% error on complex stacking scenarios
- 0% error on critical mechanics (type chart, STAB, etc.)

### 8.3 Performance Goals
- Single calculation: <1ms
- Full battle simulation (10 turns): <50ms
- Deep analysis mode: <5s

---

## 9. Priority Test Implementation Order

### Immediate (This Week)
1. ✅ Review existing test coverage - DONE
2. Add critical hit stage tests
3. Add multi-hit move tests
4. Verify paralysis Gen 7+ behavior
5. Add priority bracket tests

### Short Term (Next 2 Weeks)
6. Complex ability interactions (Mold Breaker, Wonder Guard)
7. Recoil move calculations
8. Accuracy/evasion stage tests
9. Fixed damage moves
10. Two-turn move mechanics

### Medium Term (Next Month)
11. In-game validation with battle recordings
12. Mega Evolution stat changes
13. Weather-dependent form changes
14. OHKO move mechanics
15. Performance optimization tests

### Long Term (Ongoing)
16. Continuous validation against new Emerald Rogue versions
17. Coverage expansion as new features are added
18. Community-reported edge cases
19. Integration with other tools (damage calculator websites)

---

## 10. Test Execution Checklist

### Before Each Test Session
- [ ] Update to latest Emerald Rogue version
- [ ] Review recent patch notes for mechanic changes
- [ ] Prepare test battle scenarios
- [ ] Set up recording tools

### During Testing
- [ ] Record all inputs (Pokemon stats, moves, conditions)
- [ ] Capture actual damage values
- [ ] Note any unexpected behaviors
- [ ] Screenshot key moments

### After Testing
- [ ] Compare actual vs expected results
- [ ] Document discrepancies
- [ ] Update test cases
- [ ] Commit test data to repository
- [ ] Update this document with findings

---

## 11. Contact and Resources

### Emerald Rogue Resources
- **GitHub:** https://github.com/Pokabbie/pokeemerald-rogue
- **Discord:** [Link if available]
- **Documentation:** Check repo wiki

### Pokemon Mechanics References
- **Bulbapedia:** https://bulbapedia.bulbagarden.net/
- **Smogon:** https://www.smogon.com/dex/
- **Serebii:** https://www.serebii.net/

### Testing Tools
- Damage calc: https://calc.pokemonshowdown.com/
- Type chart: https://pokemondb.net/type

---

## Appendix A: Test File Structure

```
app/src/test/java/com/ercompanion/calc/
├── DamageCalculatorTest.kt          # Core damage formula
├── AbilityEffectsTest.kt            # Ability interactions
├── WeatherEffectsTest.kt            # Weather and terrain
├── ItemEffectsTest.kt               # Held items
├── MoveSimulatorTest.kt             # Status and moves
├── OptimalLineCalculatorTest.kt     # Strategy calculations
├── DeepAnalysisModeTest.kt          # Advanced analysis
├── CriticalHitTest.kt               # ⚠️ TO CREATE
├── MultiHitMovesTest.kt             # ⚠️ TO CREATE
├── PriorityTest.kt                  # ⚠️ TO CREATE
├── AccuracyEvasionTest.kt           # ⚠️ TO CREATE
├── RecoilMovesTest.kt               # ⚠️ TO CREATE
├── FixedDamageTest.kt               # ⚠️ TO CREATE
├── TwoTurnMovesTest.kt              # ⚠️ TO CREATE
└── ValidationTest.kt                # ⚠️ TO CREATE - In-game comparison
```

---

## Appendix B: Example Test Template

```kotlin
package com.ercompanion.calc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [Feature Name]
 *
 * Covers:
 * - [Mechanic 1]
 * - [Mechanic 2]
 * - Edge cases
 *
 * References:
 * - Bulbapedia: [URL]
 * - Emerald Rogue: [Commit/version]
 */
class FeatureTest {

    @Test
    fun testBasicBehavior() {
        // Arrange
        val input = createTestData()

        // Act
        val result = calculator.calculate(input)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun testEdgeCase() {
        // Test extreme values, boundaries, etc.
    }

    @Test
    fun testEmeraldRogueSpecific() {
        // Test behavior specific to Emerald Rogue
        // Reference: [source]
    }
}
```

---

**Document Version:** 1.0
**Last Test Run:** [To be filled]
**Next Review Date:** [To be scheduled]
