# Item ID Reference for Phase 7 Implementation

## Quick Reference
This document lists all item IDs used in the ItemEffects system for easy reference.

## Power Items

### Choice Items (Lock Move, 1.5x Boost)
- **222** - Choice Band (Physical Attack)
- **223** - Choice Specs (Special Attack)
- **224** - Choice Scarf (Speed)

### Damage Boosting Items
- **225** - Life Orb (1.3x all damage, 10% recoil)
- **226** - Expert Belt (1.2x super-effective)
- **227** - Muscle Band (1.1x physical)
- **228** - Wise Glasses (1.1x special)

## Type-Boosting Items (1.2x for matching type)

| Item ID | Name | Type Boosted |
|---------|------|--------------|
| 250 | Charcoal | Fire (9) |
| 251 | Mystic Water | Water (10) |
| 252 | Miracle Seed | Grass (11) |
| 253 | Magnet | Electric (12) |
| 254 | Never-Melt Ice | Ice (14) |
| 255 | Black Belt | Fighting (1) |
| 256 | Poison Barb | Poison (3) |
| 257 | Soft Sand | Ground (4) |
| 258 | Sharp Beak | Flying (2) |
| 259 | Twisted Spoon | Psychic (13) |
| 260 | Silver Powder | Bug (6) |
| 261 | Hard Stone | Rock (5) |
| 262 | Spell Tag | Ghost (7) |
| 263 | Dragon Fang | Dragon (15) |
| 264 | Black Glasses | Dark (16) |
| 265 | Metal Coat | Steel (8) |

## Recovery Items

- **234** - Leftovers (Heal 1/16 max HP per turn)
- **282** - Black Sludge (Heal 1/16 max HP for Poison types, damage others)

## Survival Items

- **235** - Focus Sash (Survive at 1 HP from full HP)
- **275** - Focus Band (10% chance to survive at 1 HP)

## Status Orbs

- **280** - Flame Orb (Burns holder after turn 1)
- **281** - Toxic Orb (Badly poisons holder after turn 1)

## Speed Modification

- **224** - Choice Scarf (1.5x speed, locks move)
- **285** - Iron Ball (0.5x speed)

## Type Mappings

For reference, type IDs used in the codebase:

| Type ID | Type Name |
|---------|-----------|
| 0 | Normal |
| 1 | Fighting |
| 2 | Flying |
| 3 | Poison |
| 4 | Ground |
| 5 | Rock |
| 6 | Bug |
| 7 | Ghost |
| 8 | Steel |
| 9 | Fire |
| 10 | Water |
| 11 | Grass |
| 12 | Electric |
| 13 | Psychic |
| 14 | Ice |
| 15 | Dragon |
| 16 | Dark |
| 17 | Fairy |

## Move Categories

| Category ID | Category Name |
|-------------|---------------|
| 0 | Physical |
| 1 | Special |
| 2 | Status |

## Usage in Code

### Checking for Specific Items
```kotlin
if (battler.mon.heldItem == 225) {  // Life Orb
    // Apply Life Orb logic
}
```

### Using ItemEffects Functions
```kotlin
// Check if Choice item
val isChoice = ItemEffects.isChoiceItem(battler.mon.heldItem)

// Get damage multiplier
val moveData = PokemonData.getMoveData(moveId)
val multiplier = ItemEffects.getDamageMultiplier(
    itemId = battler.mon.heldItem,
    moveData = moveData,
    effectiveness = 2.0f  // Super-effective
)

// Apply Life Orb recoil
state = ItemEffects.applyLifeOrbRecoil(state, isPlayer = true, moveUsed = true)

// Apply Leftovers healing
state = ItemEffects.applyEndOfTurnHealing(state, isPlayer = true)

// Check Focus Sash
val adjustedDamage = ItemEffects.checkFocusItem(
    battler = defender,
    incomingDamage = 150,
    wasFullHP = true
)
```

## Notes

1. **Item ID Discrepancies**: The item IDs used here are based on the ItemData.kt constants. If you're working with raw save state data, make sure to verify the actual item IDs used by Pokemon Emerald Rogue.

2. **Choice Items**: When a Choice item is equipped, the battler is locked into the first move used. This is tracked via `TempBoosts.lockedMove` and `TempBoosts.lockedTurns`.

3. **Life Orb Recoil**: Applied after each damaging move, not status moves. The recoil is exactly 1/10 of max HP.

4. **Black Sludge**: Automatically checks if the holder is Poison type. If not, it damages instead of healing.

5. **Focus Sash**: Only works once when at full HP. After activation, it becomes useless until the Pokemon switches out.

6. **Status Orbs**: Activate at the end of the first turn (turn 1), not turn 0. They don't overwrite existing status conditions.

## Testing Item Effects

When writing tests or debugging:

```kotlin
// Create test Pokemon with item
val testMon = createTestMon(
    species = 6,  // Charizard
    maxHp = 150,
    hp = 150,
    heldItem = 225  // Life Orb
)

// Create battle state
val player = createBattlerState(testMon)
val state = BattleState(player, enemy)

// Test item effect
val newState = ItemEffects.applyLifeOrbRecoil(state, isPlayer = true, moveUsed = true)
assertEquals(135, newState.player.currentHp)  // 150 - 15 = 135
```
