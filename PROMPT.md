# ER Companion App — Android Proof of Concept

## Project Overview

Build an Android companion app for **Pokémon Emerald Rogue** running on an **AYN Thor** dual-screen handheld gaming device. The app runs on the **bottom screen** while mGBA/RetroArch runs on the top screen.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **Build:** Gradle with Kotlin DSL

## Communication

**RetroArch Network Commands** — UDP on `localhost:55355`

The app sends UDP commands to RetroArch (which runs the mGBA core) to read GBA memory:

```
READ_CORE_MEMORY <hex_address> <num_bytes>
```

RetroArch responds with:
```
READ_CORE_MEMORY <hex_address> <hex_byte1> <hex_byte2> ...
```

Enable in RetroArch settings: `Settings > Network > Network Commands = ON`

## GBA Memory Layout (Emerald base — pret/pokeemerald / DepressoMocha emerogue)

GBA EWRAM starts at `0x02000000`. Key addresses (confirmed from source):

```kotlin
// These are APPROXIMATE — need to be confirmed from build .map file
// or via runtime scanner. The app should scan to find them.
const val EWRAM_BASE = 0x02000000L

// Party data — scan for these
// struct Pokemon = 104 bytes each, 6 slots
// gPlayerPartyCount: 1 byte
// gPlayerParty: 6 x Pokemon structs (624 bytes total)
// gEnemyPartyCount: 1 byte  
// gEnemyParty: 6 x Pokemon structs

// Known Emerald approximate offsets (verify at runtime):
const val PLAYER_PARTY_COUNT_APPROX = 0x020244ECL // adjust based on scan
const val PLAYER_PARTY_APPROX = 0x020244F0L       // adjust based on scan
```

### Pokemon Struct Layout (104 bytes)

```kotlin
// struct BoxPokemon (80 bytes) — encrypted substructures
// offset 0x00: personality (u32) — encryption seed part 1
// offset 0x04: otId (u32) — encryption seed part 2
// offset 0x08: nickname (10 bytes)
// offset 0x12: language (u8, 3 bits)
// ... encrypted substructures (4 x 12 bytes = 48 bytes)
//     order determined by personality % 24
//     each decrypted with key = personality XOR otId
//     substructure A: species, item, experience, friendship
//     substructure B: moves (4 x move ID + PP)
//     substructure C: EVs, condition
//     substructure D: IVs, ribbons, ability, nature (bits)

// struct Pokemon extra fields (after BoxPokemon, offset 0x50):
// offset 0x50: status (u32)
// offset 0x54: level (u8)
// offset 0x55: mail (u8)
// offset 0x56: hp (u16)
// offset 0x58: maxHP (u16)
// offset 0x5A: attack (u16)
// offset 0x5C: defense (u16)
// offset 0x5E: speed (u16)
// offset 0x60: spAttack (u16)
// offset 0x62: spDefense (u16)
// Total: 104 bytes confirmed by STATIC_ASSERT in source
```

### Substructure Order

The order of the 4 substructures (ABCD) is determined by `personality % 24`. Use this lookup:

```kotlin
val SUBSTRUCTURE_ORDER = arrayOf(
    intArrayOf(0,1,2,3), intArrayOf(0,1,3,2), intArrayOf(0,2,1,3),
    intArrayOf(0,3,1,2), intArrayOf(0,2,3,1), intArrayOf(0,3,2,1),
    intArrayOf(1,0,2,3), intArrayOf(1,0,3,2), intArrayOf(2,0,1,3),
    intArrayOf(3,0,1,2), intArrayOf(2,0,3,1), intArrayOf(3,0,2,1),
    intArrayOf(1,2,0,3), intArrayOf(1,3,0,2), intArrayOf(2,1,0,3),
    intArrayOf(3,1,0,2), intArrayOf(2,3,0,1), intArrayOf(3,2,0,1),
    intArrayOf(1,2,3,0), intArrayOf(1,3,2,0), intArrayOf(2,1,3,0),
    intArrayOf(3,1,2,0), intArrayOf(2,3,1,0), intArrayOf(3,2,1,0),
)
```

Decrypt each 12-byte substructure by XORing 32-bit words with `personality XOR otId`.

## Runtime Address Scanner

Since exact EWRAM offsets vary by build, implement a scanner:

1. Read 624KB of EWRAM starting at `0x02000000`
2. Walk through 4-byte aligned positions
3. At each position, check if it looks like a valid party count (value 1-6)
4. Then check if the next 104 bytes look like a valid Pokémon struct:
   - personality != 0
   - After decryption, species ID in range 1-412 (Gen3 national dex)
   - Level in range 1-100
5. Confirm by checking all party slots are consistent
6. Cache the found offset

## What to Build

### Phase 1 — Proof of Concept (this task)

Build a minimal but working Android app:

1. **`RetroArchClient.kt`** — UDP socket client
   - `connect(host: String = "127.0.0.1", port: Int = 55355)`
   - `readMemory(address: Long, numBytes: Int): ByteArray?`
   - `getStatus(): String?`
   - Handle timeouts gracefully (RetroArch may not be running)
   - Poll every 500ms via coroutine

2. **`Gen3PokemonParser.kt`** — parse raw bytes into data classes
   - `data class PartyMon(val species: Int, val level: Int, val hp: Int, val maxHp: Int, val nickname: String, val moves: List<Int>)`
   - Implement the BoxPokemon decryption (personality XOR otId, substructure reorder)
   - Map species IDs to names (include a hardcoded map for Gen1-3 species, ~412 entries)
   - Map move IDs to names (include top 50 most common moves at minimum)

3. **`AddressScanner.kt`** — find party address at runtime
   - Read chunks of EWRAM and locate gPlayerParty
   - Cache result in SharedPreferences
   - Include known approximate addresses as starting hints

4. **`MainViewModel.kt`** — state management
   - Hold `partyState: StateFlow<List<PartyMon>>`
   - Hold `connectionState: StateFlow<ConnectionState>` (DISCONNECTED/CONNECTED/ERROR)
   - Poll loop with coroutines

5. **`MainActivity.kt`** + **`MainScreen.kt`** — Jetpack Compose UI
   - Show connection status (green dot / red dot)
   - Party panel: 6 slots, each showing species name + level + HP bar
   - Tap a slot to expand: show moves
   - Clean, dark theme (good for gaming)

### Project Structure

```
er-companion/
├── app/
│   ├── src/main/
│   │   ├── java/com/ercompanion/
│   │   │   ├── network/RetroArchClient.kt
│   │   │   ├── parser/Gen3PokemonParser.kt
│   │   │   ├── parser/AddressScanner.kt
│   │   │   ├── data/PokemonData.kt        (species/move name maps)
│   │   │   ├── ui/MainScreen.kt
│   │   │   ├── ui/theme/Theme.kt
│   │   │   ├── MainViewModel.kt
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   └── values/strings.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── wrapper/
        ├── gradle-wrapper.properties
        └── gradle-wrapper.jar
```

## Key Requirements

- **Permissions needed:** `INTERNET` only (no special permissions for localhost UDP)
- **No root required** — RetroArch exposes UDP without root
- **Dark theme** — this is a gaming companion, dark UI is appropriate
- **Responsive** — polling must not block UI thread, use Kotlin coroutines
- **Error handling** — graceful when RetroArch not running (show "Not connected")
- **AYN Thor resolution** — 1080p bottom screen, design for full-width layout

## Notes

- Emerald Rogue is based on pokeemerald (Emerald, not FireRed) — use Emerald memory layout
- The DepressoMocha mocha patch source: https://github.com/DepressoMocha/emerogue
- The struct sizes are CONFIRMED from source: BoxPokemon=80, Pokemon=104
- For now, hardcode a Gen1 species name table (151 entries) and expand later
- Move name table: at minimum include the 4 starting moves and common ones

## Completion Signal

When you have a buildable project (even if not all features are complete — just needs to compile and show the party panel UI with mock data if real connection isn't available), run:

```
openclaw system event --text "Done: er-companion PoC built — Kotlin/Compose Android app with RetroArch UDP client and Gen3 parser scaffold" --mode now
```
