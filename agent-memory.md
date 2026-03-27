# Research Task: Memory Map Verification & Alignment for ER Companion

You are a deep research agent. Your job is to audit and harden the memory map, struct offsets, and address scanner in the ER Companion app against the actual emerogue source code.

## Repo Context
- Project: ~/Projects/er-companion
- Game: Pokémon Emerald Rogue (DepressoMocha patch) — GBA game running via mGBA core in RetroArch on Android
- Save states are read as RZIP-compressed mGBA save states
- Start by reading: MEMORY_ADDRESSES.md, app/src/main/java/com/ercompanion/parser/Gen3PokemonParser.kt, app/src/main/java/com/ercompanion/parser/AddressScanner.kt

## What to Research

### 1. Verify EWRAM Offsets Against Emerald Source
Cross-check all addresses in MEMORY_ADDRESSES.md against:
- https://github.com/pret/pokeemerald (search for gPlayerParty, gEnemyParty, gBattleMons, gPlayerPartyCount, gEnemyPartyCount)
- https://github.com/DepressoMocha/emerogue (check if any party/battle addresses differ from base Emerald)

Key addresses to verify:
- gPlayerParty at EWRAM+0x37780 (GBA: 0x02037780)
- gPlayerPartyCount at EWRAM+0x3777C (GBA: 0x0203777C)
- gBattleMons at EWRAM+0x1C358 (GBA: 0x0201C358)
- gBattlerPartyIndexes location
- gEnemyParty location

### 2. Verify BoxPokemon / Pokemon Struct Layout
The BoxPokemon struct (80 bytes) has 4 encrypted substructures. Verify:
- Personality value XOR key for decryption (checksum XOR personality)
- Substructure order from personality % 24
- Growth substructure: species (u16), item (u16), experience (u32), ppBonuses (u8), friendship (u8)
- Attacks substructure: moves[4] (u16 each), pp[4] (u8 each)
- EVs/Condition substructure: hp/attack/defense/speed/spAtk/spDef EVs (u8 each)
- Misc substructure: pokerus (u8), met location (u8), origins info (u16), IVs/egg/ability (u32), ribbons (u32)

Check Gen3PokemonParser.kt decryption logic against pret/pokeemerald source.

### 3. mGBA Save State Memory Layout
- mGBA save state: 0x61000 bytes uncompressed, EWRAM at file offset 0x21000
- Verify this offset. Is the EWRAM offset definitely 0x21000 or could it vary by mGBA version?
- Check https://github.com/mgba-emu/mgba (look for save state format / serialization)

### 4. Address Scanner Logic
Read AddressScanner.kt:
- Is the scan range appropriate?
- Could it give false positives?
- Could party detection fail for emerogue Pokémon with species IDs above 386?
- Any alignment issues?

### 5. gBattleMons Struct Field Offsets
Our empirically-found offsets:
- +0x00: species (u16)
- +0x02: attack (u16)
- +0x04: defense (u16)
- +0x06: speed (u16)
- +0x08: spAttack (u16)
- +0x0a: spDefense (u16)
- +0x0c: moves[4] (u16 x4)
- +0x18: statStages[8] (s8, baseline=6)
- +0x2a: hp (u16)
- +0x2c: level (u8)
- +0x2e: maxHP (u16)

Cross-check these against the BattlePokemon struct in pokeemerald source (include/battle.h or similar). Note any discrepancies.

## Implementation
1. Update MEMORY_ADDRESSES.md with verified addresses and source citations
2. Fix any wrong offsets in Gen3PokemonParser.kt
3. Fix any issues in AddressScanner.kt (especially for high species IDs from Gen 4-9)
4. Add validation comments in parser code citing where each offset comes from
5. If gBattleMons struct offsets are wrong, fix them in both parser and ViewModel

Commit with: "fix: verify and correct memory map offsets against pokeemerald/emerogue source"
Then run: openclaw system event --text "Done: Memory map verification + alignment fixes committed to er-companion" --mode now
