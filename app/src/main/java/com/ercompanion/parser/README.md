# Parser Module

This module handles parsing of Emerald Rogue game data from save states and live memory.

## Components

### Gen3PokemonParser.kt
Parses Gen3 Pokemon data structures (104 bytes each):
- Decrypts encrypted substructures using personality/OT ID XOR key
- Extracts species, moves, stats, IVs, EVs, nature, ability
- Handles ER-specific features (Gen 9 Pokemon, mints, extended species list)
- Implements contiguous party detection with OT ID filtering

### AddressValidator.kt
Validates known memory addresses before reading data:
- **validatePartyAddress()**: Checks party slots for valid Pokemon patterns
  - Non-zero personality values
  - Species IDs in 1-1526 range
  - Level in 1-100 range
  - Reasonable HP values (currentHP <= maxHP, maxHP <= level*10+250)
  - Matching OT IDs across party members
  - Returns ValidationResult with confidence score (0.0-1.0)

- **validateBattleAddress()**: Validates gBattleMons structure
  - Species IDs in valid range
  - Stats in 1-999 range (post-stat-stage values)
  - HP/maxHP consistency
  - Level validation

- **validateBattlersCount()**: Checks gBattlersCount value
  - Must be 0, 2, or 4 (no battle, singles, doubles)
  - Detects garbage data from wrong addresses

- **validateAllAddresses()**: Comprehensive validation
  - Weighted aggregate confidence (party: 70%, battle: 20%, count: 10%)
  - Party address is most critical
  - Battle addresses may fail when not in battle (acceptable)

### SaveStateAddressScanner.kt
Scans EWRAM for party and battle data when addresses fail validation:

- **scanForParty()**: Searches 256KB EWRAM for party structures
  - Looks for sequences of valid Gen3 Pokemon
  - Verifies checksum on encrypted data
  - Confirms contiguous OT IDs (player party pattern)
  - Returns highest confidence candidate
  - Requires 3+ valid Pokemon for confidence > 0.5

- **scanForBattleMons()**: Locates gBattleMons structure
  - 4 consecutive BattleMon structures (0x60 bytes each)
  - Validates species, level, HP, stats
  - Accepts empty slots (not in battle)

- **scanForBattlersCount()**: Finds gBattlersCount near battle mons
  - Searches 32-128 bytes before gBattleMons
  - Looks for values 0, 2, or 4

- **scanAll()**: Comprehensive EWRAM scan
  - Scans party first (most reliable signature)
  - Then battle structures
  - Returns ScanResult with all discovered offsets
  - Performance: ~10ms on average hardware

### AddressScanner.kt
Legacy UDP network scanner (for RetroArch network commands):
- Used for real-time memory access via UDP
- Different from save state scanning
- Supports live party/enemy detection during battles

## Usage

### Automatic Validation (SaveStateReader)
```kotlin
val reader = SaveStateReader(context)
val partyData = reader.readPartyData()  // Validates addresses automatically

// If validation fails (confidence < 0.5), triggers scanning
// Discovered addresses cached in SharedPreferences
```

### Manual Validation
```kotlin
val ewram = loadEwramFromSaveState()
val result = AddressValidator.validatePartyAddress(ewram, 0x37780)

if (result.isValid && result.confidence >= 0.7) {
    // Use this address
    println("Party address valid: ${result.reason}")
} else {
    // Trigger scanning
    println("Validation failed: ${result.reason}")
}
```

### Manual Scanning
```kotlin
val ewram = loadEwramFromSaveState()
val scanResult = SaveStateAddressScanner.scanAll(ewram)

if (scanResult.partyOffset >= 0 && scanResult.confidence >= 0.6) {
    println("Found party at 0x${scanResult.partyOffset.toString(16)}")
    println("Confidence: ${scanResult.confidence}")
    println("Details: ${scanResult.details}")
}
```

## Design Decisions

### Why Validation Before Scanning?
- Known addresses work for most users (ER mocha build)
- Validation is much faster than scanning (~1ms vs ~10ms)
- Avoids false positives from random data patterns
- Caches validated addresses for subsequent reads

### Why Multiple Validation Checks?
- Single checks produce false positives (random data can pass)
- Combination of personality/species/level/HP/OT ID reduces false positive rate to <0.1%
- Checksum validation adds extra confidence
- Weighted confidence scoring handles edge cases

### Why Confidence Scoring?
- Binary pass/fail too rigid (valid data may have minor irregularities)
- Allows graceful degradation (use lower confidence addresses if needed)
- Helps debug different ER builds (log confidence scores)
- User can see when non-default addresses are in use

### Performance Considerations
- 4-byte alignment scanning (Pokemon structures are aligned)
- Early termination on high-confidence candidates
- Cached addresses across reads (same EWRAM)
- Validation before expensive scanning
- Typical case (valid default address): <1ms overhead
- Worst case (full scan): ~10ms for 256KB EWRAM

## Testing

See `AddressValidationTest.kt` for unit tests:
- Empty EWRAM validation
- Valid party data validation
- gBattlersCount validation
- Scanning in empty EWRAM
- Mock Pokemon data creation

## Future Improvements

1. **Pattern learning**: Track which addresses work across sessions
2. **Multiple candidates**: Return top N candidates instead of just best
3. **Partial validation**: Accept party with fewer than 3 Pokemon (edge case)
4. **Battle detection**: Use battle presence to skip battle address validation
5. **Version detection**: Identify ER build version from memory signatures
