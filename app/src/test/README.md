# ER Companion Test Suite

Comprehensive test suite with ground truth test data for the Emerald Rogue Companion app.

## Test Files

### 1. Gen3PokemonParserTest.kt
Location: `/app/src/test/java/com/ercompanion/parser/Gen3PokemonParserTest.kt`

Tests Gen3 Pokemon decryption and parsing with known ground truth data.

**Test Coverage:**
- Basic decryption and parsing of Pokemon data
- All 24 substructure orderings (personality % 24)
- IV extraction from packed 32-bit format (5 bits per IV)
- Nature calculation (personality % 25)
- Nature modification with mints (XOR with hidden nature modifier)
- Ability slot detection (0=ability1, 1=ability2, 2=hidden)
- Level fallback calculation from experience when level field is invalid
- OT ID auto-detection and filtering
- Status condition parsing (burn, poison, paralysis, etc.)
- Move and PP extraction
- Nickname decoding from Gen3 character set
- Encryption key calculation (personality XOR otId)
- Invalid data handling (personality=0, species>1526)

**Ground Truth Test Cases:**
- Pikachu (species 25) with known stats at level 50
- IV combinations: [31, 0, 15, 31, 16, 8]
- Nature tests: all 25 nature values (0-24)
- Mint modifier: base nature 5 XOR modifier 10 = effective nature 15
- All 24 substructure orderings with Mewtwo (species 150)
- Mixed personality/OT ID pairs for encryption testing

**Running Tests:**
```bash
cd /path/to/er-companion
./gradlew :app:testDebugUnitTest --tests "com.ercompanion.parser.Gen3PokemonParserTest"
```

### 2. PartyDetectionTest.kt
Location: `/app/src/test/java/com/ercompanion/parser/PartyDetectionTest.kt`

Tests party detection logic and ER-specific edge cases.

**Test Coverage:**
- Contiguous slot detection (stops at first null/invalid)
- OT ID auto-detection from first Pokemon
- OT ID mismatch detection (stops at first different OT ID)
- Corrupted party count handling (count=16+ or 0, but real party is 1-6)
- Empty party handling (all zeros)
- Full party with 6 Pokemon
- Party with gaps (should stop at first gap)
- parseAllSlots() returns all 12 slots without filtering
- Invalid data in middle of party
- Multiple OT IDs in separate parties

**ER Mocha-Specific Issues Tested:**
- Party count can be 0 even when party exists
- Party count can be corrupted to 16+ even when real party is 1-6
- Mixed player/enemy Pokemon in 12-slot buffer
- Contiguous detection is more reliable than party count field

**Running Tests:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.ercompanion.parser.PartyDetectionTest"
```

### 3. DamageCalculatorTest.kt
Location: `/app/src/test/java/com/ercompanion/calc/DamageCalculatorTest.kt`

Tests damage calculation formulas with known values.

**Test Coverage:**
- Base damage formula: `((2*Level/5 + 2) * Power * Attack / Defense / 50 + 2)`
- STAB (Same Type Attack Bonus): 1.5x multiplier
- Type effectiveness:
  - Super effective (2x, 4x)
  - Not very effective (0.5x, 0.25x)
  - Immunities (0x): Normal→Ghost, Electric→Ground, Dragon→Fairy, etc.
- Burn effect: 0.5x physical damage (unless Guts ability)
- Status moves (power=0) return 0 damage
- Level scaling: high vs low, low vs high
- Percentage calculation relative to target max HP
- KO detection (damage >= 100% HP)
- Minimum damage is always 1
- Random roll range: 85-100% (min to max damage)
- Dual-type attackers (STAB if either type matches)
- Dual-type defenders (multiply effectiveness)

**Type Chart Tests:**
- All Fighting-type matchups
- All Psychic-type matchups
- All Fairy-type matchups (Gen 6+)
- All immunity combinations (8 total in Gen 6+)

**Ground Truth Values:**
- Level 50, 100 Atk, 100 Def, 80 BP → 37 base damage
- With STAB (1.5x) → 55 damage
- With 2x effectiveness → 74 damage
- With 4x effectiveness → 148 damage
- With 0.5x effectiveness → 18 damage

**Running Tests:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.ercompanion.calc.DamageCalculatorTest"
```

### 4. MemoryAddressTest.kt
Location: `/app/src/test/java/com/ercompanion/parser/MemoryAddressTest.kt`

Tests memory layout parsing and address calculations.

**Test Coverage:**
- EWRAM memory offsets
  - Party data: EWRAM + 0x37780
  - Party count: EWRAM + 0x3777C (unreliable in ER)
  - gBattleMons: EWRAM + 0x1C358
- RASTATE format handling
  - EWRAM offset in RASTATE: 0x21000
  - Party offset in RASTATE: 0x58780
- Slot sizes
  - Party Pokemon: 104 bytes each
  - gBattleMons: 0x60 (96) bytes each
- Stat stage conversions
  - Stored as 0-12, baseline is 6
  - -6 = stored 0, 0 = stored 6, +6 = stored 12
  - Multipliers: -6=0.25x, -3=0.4x, 0=1x, +1=1.5x, +2=2x, +6=4x
- gBattleMon structure layout
  - Species, stats, moves at known offsets
  - Stat stages array at 0x18 (8 bytes)
  - HP, level, maxHP fields
- Battle detection via gBattleMons[1] (enemy slot)
- Memory boundary checks
- Little-endian byte order verification
- Status condition flags
- Species ID range (1-1526 for ER)

**Memory Map Reference:**
```
EWRAM Base: 0x02000000 (256KB)

Party Data:
  GBA Address:      0x02037780
  EWRAM Offset:     0x37780
  RASTATE Offset:   0x58780
  Size:             12 × 104 = 1248 bytes

gBattleMons:
  GBA Address:      0x0201C358
  EWRAM Offset:     0x1C358
  RASTATE Offset:   0x3D358
  Size:             4 × 96 = 384 bytes
```

**Running Tests:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.ercompanion.parser.MemoryAddressTest"
```

## Running All Tests

To run the entire test suite:

```bash
cd /path/to/er-companion
./gradlew :app:testDebugUnitTest
```

To run with verbose output:

```bash
./gradlew :app:testDebugUnitTest --info
```

To see test report:

```bash
open app/build/reports/tests/testDebugUnitTest/index.html
```

## Test Data Generation

The test suite uses helper functions to generate ground truth Pokemon data with known encryption:

### createPokemonData()
Generates a complete 104-byte Pokemon structure with:
- Personality and OT ID (for encryption key)
- Species, level, stats, experience
- Moves and PP
- IVs (packed into 32-bit format)
- Ability slot
- Status conditions
- Properly encrypted substructures

### createSubstructures()
Generates the 48 bytes of unencrypted substructure data:
- Substructure 0 (Growth): species, item, experience, friendship
- Substructure 1 (Attacks): 4 moves + 4 PP values
- Substructure 2 (EVs/IVs): packed IVs in 32-bit value
- Substructure 3 (Misc): ability slot in ivEggAbility field

### encryptSubstructures()
Encrypts substructures using the Gen3 algorithm:
- Key = personality XOR otId
- XOR each 32-bit word with key

## Known Issues

### Tests Currently Failing
Some tests may fail due to implementation details differing from test assumptions:
- Damage calculations may vary due to rounding differences
- Type effectiveness calculations may need adjustment
- Party detection logic has evolved (now stops at first OT mismatch)

### ER-Specific Quirks Tested
- Party count field is unreliable (can be 0 or 16+ even with valid party)
- Contiguous detection is more reliable than party count
- OT ID filtering is essential to separate player from enemy Pokemon
- Level field can be 0 during battle (fallback to experience-based calculation)
- Extended species range (1-1526 vs 1-386 in vanilla)

## Test Coverage Summary

| Test File | Tests | Focus Area |
|-----------|-------|------------|
| Gen3PokemonParserTest | 15+ | Encryption, decryption, parsing |
| PartyDetectionTest | 15+ | Party detection, OT filtering |
| DamageCalculatorTest | 30+ | Damage formula, type chart |
| MemoryAddressTest | 15+ | Memory layout, offsets |

**Total:** 75+ comprehensive tests with ground truth data

## Contributing

When adding new tests:
1. Use ground truth values with known expected outputs
2. Test edge cases (min/max values, invalid data)
3. Document the expected behavior in comments
4. Add test cases for ER-specific quirks if discovered

## References

- MEMORY_ADDRESSES.md - Complete memory layout documentation
- Gen3 Pokemon structure: https://bulbapedia.bulbagarden.net/wiki/Pokémon_data_structure_(Generation_III)
- Type effectiveness chart: https://pokemondb.net/type (Gen 6+)
- Damage formula: https://bulbapedia.bulbagarden.net/wiki/Damage
