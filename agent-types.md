# Task: Fill Missing Pokémon Type Data in PokemonData.kt

418 species IDs are missing from the SPECIES_TYPES map in `app/src/main/java/com/ercompanion/data/PokemonData.kt`. They all fall back to Normal type, breaking STAB and type effectiveness calculations.

## Type ID Reference
```
0=Normal, 1=Fighting, 2=Flying, 3=Poison, 4=Ground, 5=Rock,
6=Bug, 7=Ghost, 8=Steel, 9=Fire, 10=Water, 11=Grass,
12=Electric, 13=Psychic, 14=Ice, 15=Dragon, 16=Dark, 17=Fairy
```

## Missing IDs
Run this to get the full list:
```bash
python3 - <<'EOF'
import re
with open("app/src/main/java/com/ercompanion/data/PokemonData.kt") as f:
    content = f.read()
type_ids = set(int(m) for m in re.findall(r'(\d+)\s+to\s+listOf', content))
name_ids = set(int(m) for m in re.findall(r'(\d+)\s+to\s+"[^"]+"', content))
missing = sorted(name_ids - type_ids)
print(missing)
EOF
```

Also run this to see what names correspond to the missing IDs:
```bash
python3 - <<'EOF'
import re
with open("app/src/main/java/com/ercompanion/data/PokemonData.kt") as f:
    content = f.read()
type_ids = set(int(m) for m in re.findall(r'(\d+)\s+to\s+listOf', content))
names = dict((int(k), v) for k, v in re.findall(r'(\d+)\s+to\s+"([^"]+)"', content))
missing = sorted(set(names.keys()) - type_ids)
for i in missing:
    print(f"{i}: {names.get(i, '?')}")
EOF
```

## What To Do

1. Look up types for each missing species. Best sources:
   - https://github.com/DepressoMocha/emerogue — check `src/data/pokemon/species_info/` for type data (look for `TYPE_1` and `TYPE_2` fields)
   - https://bulbapedia.bulbagarden.net — for any you can't find in emerogue source
   - For forms/megas/regionals, use the correct form type (e.g. Mega Charizard X is Fire/Dragon)

2. Add ALL missing entries to the SPECIES_TYPES map in PokemonData.kt. Find the closing `}` of the map (near the end of the file) and add entries before it.

3. For IDs you genuinely can't identify (obscure forms, custom emerogue mons), default to the base form's type rather than leaving it missing.

## Important Notes
- The game uses custom species IDs for forms (e.g. Mega evolutions, regional variants, costume forms)
- IDs 906+ are mostly megas, alternate forms, and custom emerogue additions
- Cross-reference with the species name list in `speciesNames_*()` functions to identify what each ID is

## Build & Verify
Run: `./gradlew assembleDebug`
Must compile clean.

Commit with: "fix: add missing type data for 418 species (Gen 9, megas, alternate forms)"
Then run: openclaw system event --text "Done: Missing Pokemon types filled in PokemonData.kt" --mode now
