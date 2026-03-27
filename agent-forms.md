# Research Task: Special & Newer-Gen Pokémon Form Handling in ER Companion

You are a deep research agent. Your job is to investigate and then improve how the ER Companion Android app handles special forms and newer-generation Pokémon (especially Pokémon added via the DepressoMocha emerogue patch).

## Repo Context
- Project: ~/Projects/er-companion
- Game: Pokémon Emerald Rogue (emerogue) — a romhack of Pokémon Emerald that adds Gen 4–9 Pokémon
- Source references to research:
  1. https://github.com/DepressoMocha/emerogue (skim the Pokémon species tables, form tables, form-change logic)
  2. https://github.com/pret/pokeemerald (base Emerald source — for base struct definitions)
  3. Search online for "Pokemon Emerald alternate forms species ID" and "pokeemerald FORM_CHANGE_TYPE" and "Unown forms Emerald personality" and "Castform/Deoxys forms Gen 3"

## What to Research & Fix

### 1. Form Species ID Mapping
- In Gen 3 Emerald, forms like Unown, Castform, Deoxys use species IDs or substructure data to encode form
- In emerogue, newer mons (Gen 4-9) and regional variants (Alolan, Galarian, Hisuian) likely use extended species IDs or personality values
- Research: how are alternate forms encoded in the save data? Personality bits? A separate formId field? Extended species range?
- Find: what species ID ranges emerogue uses for regional variants / mega evolutions / other special forms
- Check: app/src/main/java/com/ercompanion/data/PokemonData.kt — does it handle these correctly?

### 2. Sprite URLs
- Current sprite code fetches from DepressoMocha GitHub (moka-dev branch) using species ID
- For alternate forms, are there separate sprite files (e.g. anim_front_a.png for Unown-A)?
- Research what sprite filenames exist in the emerogue repo for special forms
- Fix sprite URL construction to handle alternate form sprites correctly

### 3. Type Chart for Special Forms
- Castform changes type in weather. Rotom changes type by form. Wormadam changes type by form.
- In emerogue: Alolan Ninetales is Ice/Fairy not Fire. Galarian Ponyta is Psychic not Fire. etc.
- Check data/PokemonData.kt — does the type data correctly reflect alternate forms?
- If not, update it with the correct type overrides per form

### 4. Implementation
After research:
- Update PokemonData.kt with correct form to type mappings for all relevant Pokémon
- Fix sprite URL generation for forms that have alternate sprite files
- Add a getFormName(species: Int, personality: Int): String? utility if helpful
- Make sure the app shows correct types for alternate-form Pokémon in battle

## Output
When done, commit all changes with message: "feat: improve special/alternate form handling for Gen 3-9 mons"
Then run: openclaw system event --text "Done: Form handling research + fixes committed to er-companion" --mode now
