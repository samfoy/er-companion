# Emerald Rogue Memory Addresses

## Memory Layout

### EWRAM Base
- **GBA Address**: `0x02000000`
- **Size**: 256KB (0x40000 bytes)

### Player Party
- **Address**: `0x02037780` (EWRAM+0x37780)
- **Structure**: 12 slots × 104 bytes each = 1248 bytes
- **Format**: Gen3 Pokemon structure (encrypted substructures)

#### Party Count
- **Address**: `0x0203777C` (EWRAM+0x3777C)
- **Size**: 1 byte
- **Note**: **UNRELIABLE** in ER mocha - can be 0 even when party exists. Use contiguous slot detection instead.

### Party Structure
```
Slot 0-11: Player party (contiguous from slot 0 until first empty)
  - Read all 12 slots
  - Take contiguous Pokemon from slot 0 (stop at first null)
  - All player Pokemon share the same OT ID
```

### Battle Data (gBattleMons)
- **Address**: `0x0201C358` (EWRAM+0x1C358)
- **Structure**: 4 slots × 0x60 bytes each
  - Slot 0: Player's active Pokemon
  - Slot 1: Enemy's active Pokemon
  - Slot 2-3: Doubles battle (player ally, enemy ally)

#### gBattleMons Structure (0x60 bytes per mon)
```
+0x00: species (u16)
+0x02: attack (u16)
+0x04: defense (u16)
+0x06: speed (u16)
+0x08: spAttack (u16)
+0x0A: spDefense (u16)
+0x0C: moves[4] (u16 each)
+0x18: statStages[8] (s8, baseline=6)
+0x2A: hp (u16)
+0x2C: level (u8)
+0x2E: maxHP (u16)
```

## Pokemon Data Structure (104 bytes)

### BoxPokemon (80 bytes)
```
+0x00: personality (u32)
+0x04: otId (u32)
+0x08: nickname[10] (Gen3 encoded)
+0x12: language (u16)
+0x14: otName[7] (Gen3 encoded)
+0x1B: markings (u8)
+0x1C: checksum (u16)
+0x1E: padding (u16)
+0x20: encrypted data (48 bytes)
  - 4 substructures × 12 bytes
  - Order determined by personality % 24
  - Decrypted with key = personality ^ otId
```

#### Substructure Order Table
```
personality % 24 → [substruct_0, substruct_1, substruct_2, substruct_3]
```

#### Substructures (after decryption and reordering)
```
Substruct 0 (Growth):
  +0x00: species (11 bits), heldItem (10 bits), teraType (5 bits), unused (6 bits)
  +0x04: experience (21 bits), unused (11 bits)
  +0x08: ppBonuses (u8)
  +0x09: friendship (u8)
  +0x0A: pokeball (5 bits), filler (11 bits)

Substruct 1 (Attacks):
  +0x00: moves[4] (u16 each)
  +0x08: pp[4] (u8 each)

Substruct 2 (EVs & Condition):
  +0x00: IVs packed in u32 (5 bits each for HP/Atk/Def/Spd/SpA/SpD)
  +0x04: EVs (6 bytes)
  +0x0A: condition (u8)
  +0x0B: level (u8)

Substruct 3 (Misc):
  +0x00: pokerus (u8)
  +0x01: metLocation (u8)
  +0x02: originsInfo (u16)
  +0x04: ivEggAbility (u32)
  +0x08: ribbons (u32)
```

### Party Pokemon Extra Data (24 bytes after BoxPokemon)
```
+0x50: status (u32) - flags for sleep/poison/burn/freeze/paralysis
+0x54: level (u8)
+0x55: pokerus remaining (u8)
+0x56: current HP (u16)
+0x58: max HP (u16)
+0x5A: attack (u16)
+0x5C: defense (u16)
+0x5E: speed (u16)
+0x60: spAttack (u16)
+0x62: spDefense (u16)
```

## IV Extraction
IVs are packed in a 32-bit value at Substruct 2 offset 0x00:
```kotlin
val ivData = readU32(substruct2, 0)
val ivHp = (ivData >> 0) & 0x1F
val ivAttack = (ivData >> 5) & 0x1F
val ivDefense = (ivData >> 10) & 0x1F
val ivSpeed = (ivData >> 15) & 0x1F
val ivSpAttack = (ivData >> 20) & 0x1F
val ivSpDefense = (ivData >> 25) & 0x1F
```

## Nature Calculation
```kotlin
val nature = personality % 25
// 0=Hardy, 1=Lonely, 2=Brave, etc.
```

## Hidden Power Calculation (Gen 9 mechanics)
```kotlin
// Type: uses bit 0 of each IV
val typeIndex = ((ivHp&1) + (ivAtk&1)*2 + (ivDef&1)*4 +
                 (ivSpd&1)*8 + (ivSpA&1)*16 + (ivSpD&1)*32) * 17 / 63

// Power: uses bit 1 of each IV
val power = 30 + (((ivHp>>1)&1) + ((ivAtk>>1)&1)*2 + ((ivDef>>1)&1)*4 +
                  ((ivSpd>>1)&1)*8 + ((ivSpA>>1)&1)*16 + ((ivSpD>>1)&1)*32) * 40 / 63
```

## Species ID Range
- **Emerald Rogue**: 1-1526 species (includes Gen 1-9 Pokemon)
- **Validation**: Check `species in 1..1526 && level in 1..100`

## Status Condition Flags (32-bit)
```
0x00 = None
0x01-0x07 = Sleep (turns remaining)
0x08 = Poisoned
0x10 = Burned
0x20 = Frozen
0x40 = Paralyzed
0x80 = Toxic poisoned
```

## Catch Rate Multipliers
- Sleep/Freeze: 2.0x
- Paralysis/Burn/Poison: 1.5x
- None: 1.0x

## Notes

### Emerald Rogue Specifics
1. **Party count is unreliable**: Use contiguous slot detection instead
2. **Extended species list**: Supports up to 1526 species (Gen 1-9)
3. **Gen 9 mechanics**: Hidden Power supports all 18 types including Fairy
4. **Stats are pre-calculated**: Stats in memory already include IVs/EVs/Nature modifiers
5. **OT ID filtering**: Required to separate player Pokemon from enemy/wild Pokemon in the 12-slot buffer

### UDP Streaming vs Save States
- **Save States**: Use RASTATE format with MEM chunk containing raw mGBA state
  - EWRAM offset in save state: 0x21000
- **UDP Streaming**: Direct memory access via RetroArch network commands
  - Read from GBA address space (0x02000000+)
  - More reliable for real-time battle data

### Battle Detection
- Check `gBattleMons[1]` for active enemy Pokemon
- Species > 0 indicates battle is active
- Use gBattleMons for live HP/stats during battle (includes stat stage modifiers)
- Use party structure for base stats and IV/nature info
