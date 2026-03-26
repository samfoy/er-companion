# Optimal Battle Line Calculator

## Overview

The battle line calculator computes optimal move sequences (1-3 turns) for singles battles in ER Companion. It considers setup moves, stat boosts, items, abilities, and enemy AI to recommend the best strategies.

## Architecture

### Core Components

1. **BattleState.kt** - State management
   - `BattleState`: Current battle state (player, enemy, turn)
   - `BattlerState`: Individual battler state (HP, stat stages, status)
   - `StatStages`: Stat stage modifiers (-6 to +6)
   - `TempBoosts`: Temporary effects (protect, charge, flinch)

2. **MoveEffects.kt** - Move effect database
   - Contains stat-changing effects for setup moves
   - Distinguishes between USER and TARGET effects
   - Includes common moves: Swords Dance, Dragon Dance, Calm Mind, etc.

3. **MoveSimulator.kt** - Battle simulation
   - `simulateMove()`: Execute a move and return new state
   - `calculateDamageWithStages()`: Damage calculation with stat modifiers
   - `getStatMultiplier()`: Convert stat stages to multipliers (Gen 3+ formula)
   - `calculateMoveOrder()`: Determine who moves first

4. **LineEvaluator.kt** - Move sequence scoring
   - `BattleLine`: Represents a move sequence with metadata
   - `evaluateLine()`: Score a line (0-10) based on multiple factors
   - `compareLines()`: Determine which line is better
   - `calculateSurvivalProbability()`: Estimate survival chance

5. **OptimalLineCalculator.kt** - Main entry point
   - `calculateOptimalLines()`: Returns top N best move sequences
   - `isSetupWorthwhile()`: Checks if setup is better than direct attack
   - `calculateSpeedTier()`: Speed tier analysis

## Stat Stage System

### Formula (Gen 3+)

Stat stages range from -6 to +6, with 0 as baseline.

```
Multiplier = (2 + max(0, stage)) / (2 + max(0, -stage))
```

### Multiplier Table

| Stage | Multiplier | Effect |
|-------|-----------|--------|
| -6 | 0.25x | 1/4 stat |
| -5 | 0.286x | 2/7 stat |
| -4 | 0.333x | 1/3 stat |
| -3 | 0.4x | 2/5 stat |
| -2 | 0.5x | 1/2 stat |
| -1 | 0.667x | 2/3 stat |
| 0 | 1.0x | Normal |
| +1 | 1.5x | 1.5x stat |
| +2 | 2.0x | 2x stat |
| +3 | 2.5x | 2.5x stat |
| +4 | 3.0x | 3x stat |
| +5 | 3.5x | 3.5x stat |
| +6 | 4.0x | 4x stat |

## Common Setup Moves

### Physical Attackers
- **Swords Dance** (ID: 14): +2 Attack
- **Dragon Dance** (ID: 349): +1 Attack, +1 Speed
- **Bulk Up** (ID: 339): +1 Attack, +1 Defense

### Special Attackers
- **Nasty Plot** (ID: 417): +2 Sp. Attack
- **Calm Mind** (ID: 347): +1 Sp. Attack, +1 Sp. Defense

### Speed Boosters
- **Agility** (ID: 97): +2 Speed
- **Rock Polish** (ID: 173): +2 Speed (if in ER)

### Defensive
- **Iron Defense** (ID: 334): +2 Defense

## Scoring System

Battle lines are scored from 0.0 to 10.0 based on:

### Primary Factors
1. **Turns to KO** (up to 5 points)
   - 1HKO = 5 points
   - 2HKO = 4 points
   - 3HKO = 3 points
   - No KO = partial credit based on damage

2. **Survival Probability** (up to 3 points)
   - Based on remaining HP after sequence
   - HP > 50% = 1.0 survival
   - HP < 10% = 0.2 survival

3. **Efficiency** (up to 1.5 points)
   - Damage per turn
   - Rewards high damage output

### Bonuses/Penalties
- **Clean Sweep Bonus** (+0.5): Quick KO with minimal damage
- **Risk Penalty** (-1.0): Heavy damage taken without securing KO

## Usage Example

```kotlin
val lines = OptimalLineCalculator.calculateOptimalLines(
    player = playerMon,
    enemy = enemyMon,
    maxDepth = 2,  // Look ahead 2 turns
    topN = 3,      // Return top 3 lines
    isTrainer = true
)

lines.forEach { line ->
    println("${line.description} - Score: ${line.score}/10")
    println("  Turns to KO: ${line.turnsToKO}")
    println("  Survival: ${(line.survivalProbability * 100).toInt()}%")
}
```

### Sample Output

```
1. Dragon Dance → Outrage (2HKO)
   Score: 9.2/10
   Turns to KO: 2
   Survival: 90%

2. Close Combat → Close Combat (2HKO)
   Score: 8.5/10
   Turns to KO: 2
   Survival: 70%

3. Swords Dance → Close Combat ⚠ Risky
   Score: 6.8/10
   Turns to KO: 2
   Survival: 30%
```

## Algorithm Details

### Line Generation

1. **Depth 1**: All single moves
2. **Depth 2**: All 2-move combinations
3. **Depth 3**: Selective patterns
   - Setup → Attack → Attack
   - Attack → Attack → Attack
   - Avoids exponential explosion

### Enemy Prediction

Uses `BattleAISimulator` to predict enemy moves based on:
- Wild AI flags (by level)
- Trainer AI flags (standard)
- Type effectiveness
- Damage potential

### Simulation

For each move sequence:
1. Create initial battle state
2. For each move:
   - Determine move order (speed check)
   - Apply damage
   - Apply stat changes
   - Check for KO
3. Evaluate final state

## Performance

- Target: <100ms for calculation
- Optimization strategies:
  - Limited depth (max 3 turns)
  - Selective patterns for depth 3
  - Early termination on KO
  - Caching of damage calculations

## Testing

Tests are located in:
- `MoveSimulatorTest.kt`: Stat stage mechanics
- `OptimalLineCalculatorTest.kt`: Line calculation and scoring

Run tests:
```bash
./gradlew :app:testDebugUnitTest --tests "com.ercompanion.calc.*"
```

## Future Enhancements

### Phase 1 (Current)
- ✅ Basic stat stage system
- ✅ Setup move detection
- ✅ Simple line evaluation
- ✅ 1-2 turn lookahead

### Phase 2 (Planned)
- [ ] 3-turn advanced patterns
- [ ] Priority move handling
- [ ] Choice item lock-in detection
- [ ] Status condition effects (burn, paralysis)

### Phase 3 (Future)
- [ ] Weather effects
- [ ] Ability interactions (Moxie, Speed Boost)
- [ ] Item effects (Life Orb recoil)
- [ ] Multi-target moves (doubles)

### Phase 4 (Advanced)
- [ ] Switchout consideration
- [ ] Entry hazards
- [ ] Team-wide strategy

## Integration Points

### With UI
Add to `CompactBattleScreen.kt`:
```kotlin
@Composable
fun OptimalLineDisplay(
    player: PartyMon,
    enemy: PartyMon,
    viewModel: MainViewModel
) {
    val lines = remember(player, enemy) {
        OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 2,
            topN = 3
        )
    }

    // Display lines in collapsible card
    // Color-code by safety (green/yellow/red)
}
```

### With Existing Systems
- Uses `DamageCalculator` for base calculations
- Uses `BattleAISimulator` for enemy prediction
- Uses `PokemonData` for move/species data
- Uses `PartyMon` data structure

## Notes

- Currently singles battles only
- Assumes perfect information (no random variance in AI)
- Uses simplified survival probability model
- Does not account for critical hits
- Does not handle weather/terrain
