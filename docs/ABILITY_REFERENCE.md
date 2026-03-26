# Pokemon Ability Reference for ER Companion

## Ability IDs and Effects

This document lists all abilities implemented in the ER Companion battle calculator, organized by their battle relevance.

## Implemented Abilities

### Switch-In Abilities

| ID  | Name       | Effect                                                    | Implementation |
|-----|------------|-----------------------------------------------------------|----------------|
| 22  | Intimidate | Lowers opponent's Attack by 1 stage on switch-in         | ✅ Complete    |
| 88  | Download   | Raises Attack or Sp.Attack by 1 stage (based on opponent's lower defense) | ✅ Complete    |

### On-KO Abilities

| ID  | Name        | Effect                                      | Implementation |
|-----|-------------|---------------------------------------------|----------------|
| 153 | Moxie       | Raises Attack by 1 stage when KOing opponent | ✅ Complete    |
| 224 | Beast Boost | Raises highest stat by 1 stage when KOing opponent | ✅ Complete    |

### End-of-Turn Abilities

| ID  | Name        | Effect                                     | Implementation |
|-----|-------------|--------------------------------------------|----------------|
| 3   | Speed Boost | Raises Speed by 1 stage at end of each turn | ✅ Complete    |
| 61  | Shed Skin   | 33% chance to cure status at end of each turn | ✅ Complete    |

### Stat-Boosting Abilities (Status-Dependent)

| ID | Name         | Effect                                          | Implementation |
|----|--------------|------------------------------------------------|----------------|
| 62 | Guts         | 1.5x physical Attack when statused             | ✅ Complete    |
| 63 | Marvel Scale | 1.5x physical Defense when statused            | ✅ Complete    |

### Stat-Boosting Abilities (Always Active)

| ID  | Name       | Effect                          | Implementation     |
|-----|------------|--------------------------------|--------------------|
| 37  | Huge Power | 2x physical Attack (always)    | ⚠️ Already in stats |
| 74  | Pure Power | 2x physical Attack (always)    | ⚠️ Already in stats |
| 169 | Fur Coat   | 2x physical Defense (always)   | ✅ Complete        |

**Note:** Huge Power and Pure Power are already included in the stats read from memory, so they are NOT re-applied by the calculator.

### Move Power Modifiers

| ID  | Name        | Effect                                          | Implementation |
|-----|-------------|-------------------------------------------------|----------------|
| 101 | Technician  | 1.5x power for moves with ≤60 base power       | ✅ Complete    |
| 89  | Iron Fist   | 1.2x power for punch moves                     | ✅ Complete    |
| 173 | Strong Jaw  | 1.5x power for bite moves                      | ✅ Complete    |
| 181 | Tough Claws | 1.3x power for contact moves                   | ✅ Complete    |
| 125 | Sheer Force | 1.3x power for moves with secondary effects    | ✅ Complete    |

### STAB Modifiers

| ID | Name         | Effect                        | Implementation     |
|----|--------------|-------------------------------|-------------------|
| 91 | Adaptability | STAB is 2.0x instead of 1.5x | ✅ In DamageCalculator |

### Type Immunities

| ID  | Name         | Effect                                     | Implementation     |
|-----|--------------|--------------------------------------------|--------------------|
| 26  | Levitate     | Immune to Ground-type moves               | ✅ In DamageCalculator |
| 18  | Flash Fire   | Immune to Fire-type moves                 | ✅ In DamageCalculator |
| 10  | Volt Absorb  | Immune to Electric-type moves (heals 25%) | ✅ In DamageCalculator |
| 11  | Water Absorb | Immune to Water-type moves (heals 25%)    | ✅ In DamageCalculator |
| 78  | Motor Drive  | Immune to Electric-type moves (+1 Speed)  | ✅ In DamageCalculator |
| 157 | Sap Sipper   | Immune to Grass-type moves (+1 Attack)    | ✅ In DamageCalculator |

### Defensive Type Modifiers

| ID | Name      | Effect                              | Implementation     |
|----|-----------|-------------------------------------|--------------------|
| 47 | Thick Fat | 0.5x damage from Fire and Ice moves | ✅ In DamageCalculator |

### Pinch Abilities

| ID | Name     | Effect                                      | Implementation     |
|----|----------|---------------------------------------------|--------------------|
| 65 | Overgrow | 1.5x Grass move power at ≤33% HP           | ✅ In DamageCalculator |
| 66 | Blaze    | 1.5x Fire move power at ≤33% HP            | ✅ In DamageCalculator |
| 67 | Torrent  | 1.5x Water move power at ≤33% HP           | ✅ In DamageCalculator |
| 68 | Swarm    | 1.5x Bug move power at ≤33% HP             | ✅ In DamageCalculator |

### Utility Abilities (Tracked but Not Calculated)

| ID  | Name          | Effect                                   | Implementation |
|-----|---------------|------------------------------------------|----------------|
| 5   | Sturdy        | Survives 1 HP from full health OHKO      | 🚧 Future      |
| 98  | Magic Guard   | Only damaged by direct attacks           | 🚧 Future      |
| 99  | No Guard      | All moves always hit                     | 🚧 Future      |
| 29  | Clear Body    | Prevents stat reduction                  | 🚧 Future      |
| 14  | Compound Eyes | +30% move accuracy                       | 🚧 Future      |

## Future Implementation Priorities

### High Priority (Common Competitive Abilities)

**On-Hit Abilities:**
- **Justified** (154): +1 Attack when hit by Dark-type move
- **Weak Armor** (124): +2 Speed, -1 Defense when hit by physical move

**Weather Setters:**
- **Drought** (70): Sets sun on switch-in (5 turns in Gen 6+)
- **Drizzle** (2): Sets rain on switch-in (5 turns in Gen 6+)
- **Sand Stream** (45): Sets sandstorm on switch-in (5 turns in Gen 6+)
- **Snow Warning** (117): Sets hail on switch-in (5 turns in Gen 6+)

**Switch-Out Abilities:**
- **Regenerator** (144): Heals 1/3 max HP on switch-out
- **Natural Cure** (30): Cures status condition on switch-out

### Medium Priority (Niche Competitive)

**Ability Copy:**
- **Trace** (36): Copies opponent's ability on switch-in
- **Imposter** (150): Transforms into opponent on switch-in

**Status-Related:**
- **Synchronize** (28): Passes burn/poison/paralysis to opponent
- **Poison Heal** (90): Heals 1/8 max HP when poisoned (instead of taking damage)
- **Magic Bounce** (156): Reflects status moves back to user

**Damage Modification:**
- **Multiscale** (136): 0.5x damage taken at full HP
- **Filter/Solid Rock** (111/116): 0.75x damage from super-effective moves

### Low Priority (Rare/Situational)

**Complex Mechanics:**
- **Wonder Guard** (25): Only super-effective moves deal damage
- **Moody** (141): Randomly raises one stat by 2 stages and lowers another by 1 stage each turn
- **Slow Start** (112): 0.5x Attack and Speed for first 5 turns (Regigigas)

## Move Classification for Abilities

### Punch Moves (Iron Fist)
- Fire Punch (7)
- Ice Punch (8)
- Thunder Punch (9)
- Mega Punch (5)
- Dizzy Punch (146)
- Dynamic Punch (223)
- Focus Punch (264)
- Hammer Arm (359)
- Mach Punch (183)
- Bullet Punch (418)
- Drain Punch (409)
- Shadow Punch (325)

### Bite Moves (Strong Jaw)
- Bite (44)
- Crunch (242)
- Fire Fang (424)
- Ice Fang (423)
- Thunder Fang (422)
- Poison Fang (305)

### Contact Moves (Tough Claws)
Most physical moves are contact moves, except:
- Earthquake (89)
- Dig (91)
- Rock Slide (157)
- Rock Tomb (317)
- Bullet Seed (331)
- Razor Leaf (75)
- And other projectile/ranged moves

### Secondary Effect Moves (Sheer Force)
Moves with secondary effects include:
- **Flinch:** Rock Slide, Iron Head, Waterfall, Air Slash
- **Stat changes:** Psychic, Shadow Ball, Crunch, Flamethrower
- **Status:** Thunderbolt, Ice Beam, Fire Blast, Scald
- **Confusion:** Confusion, Psybeam, Dynamic Punch

## Ability Interaction Examples

### Example 1: Intimidate Chain
```
Player switches in Gyarados (Intimidate)
→ Enemy's Attack drops to -1
Enemy switches in Arcanine (Intimidate)
→ Player's Attack drops to -1
```

### Example 2: Moxie Sweep
```
Turn 1: Salamence KOs Blissey with Outrage
→ Moxie activates: +1 Attack (now at +1)
Turn 2: Salamence KOs Chansey with Outrage
→ Moxie activates: +1 Attack (now at +2)
Turn 3: Salamence KOs Skarmory with Outrage
→ Moxie activates: +1 Attack (now at +3)
```

### Example 3: Guts + Burn
```
Heracross with Guts is burned
→ Normally: Burn halves Attack (0.5x)
→ With Guts: 1.5x Attack multiplier
→ Net effect: 0.5 * 1.5 = 0.75x Attack (still reduced, but less)
```

**Important:** In the actual Pokemon games, Guts negates the burn Attack drop entirely, giving a net 1.5x boost. The current implementation may need adjustment depending on how burn is handled in DamageCalculator.

### Example 4: Beast Boost Strategy
```
Pheromosa (Speed: 151, Attack: 137)
→ Highest stat is Speed
→ After each KO: +1 Speed
Turn 1: Speed at +1 (even faster)
Turn 2: Speed at +2 (outspeeds everything)
```

### Example 5: Download Check
```
Porygon-Z switches in
Enemy: Blissey (Defense: 10, Sp.Defense: 135)
→ Defense < Sp.Defense
→ Download boosts Attack by 1 stage
```

## Testing Checklist

When adding new abilities, ensure:
- ✅ Stat stages are clamped to [-6, +6]
- ✅ Ability triggers at the correct time (switch-in, KO, end-of-turn, etc.)
- ✅ Multipliers stack correctly with other effects
- ✅ Edge cases are handled (e.g., Intimidate on Clear Body)
- ✅ Random effects use appropriate probability distributions
- ✅ Abilities don't apply twice (e.g., Huge Power already in stats)

## References

- [Bulbapedia: Ability List](https://bulbapedia.bulbagarden.net/wiki/Ability)
- [Smogon: Ability Mechanics](https://www.smogon.com/dp/articles/abilities)
- Pokemon Emerald Randomizer documentation
- [Showdown Damage Calculator](https://calc.pokemonshowdown.com/)
