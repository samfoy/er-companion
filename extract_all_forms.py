#!/usr/bin/env python3
"""
Extract ALL species including forms from Emerald Rogue source
"""

import re
import sys

# Parse ability constants
abilities = {}
with open('/Users/samfp/emerogue/include/constants/abilities.h', 'r') as f:
    for line in f:
        match = re.match(r'#define\s+ABILITY_(\w+)\s+(\d+)', line)
        if match:
            name, id_num = match.groups()
            abilities[f'ABILITY_{name}'] = int(id_num)

print(f"Loaded {len(abilities)} ability constants", file=sys.stderr)

# Parse ALL species constants (including forms)
# First pass: find FORMS_START value
species_map = {}
forms_start = None

with open('/Users/samfp/emerogue/include/constants/species.h', 'r') as f:
    lines = f.readlines()

# First pass: get literal species IDs
for line in lines:
    match = re.match(r'#define\s+SPECIES_(\w+)\s+(\d+)', line)
    if match:
        name = match.group(1)
        species_id = int(match.group(2))
        species_map[f'SPECIES_{name}'] = species_id

# Find FORMS_START
for line in lines:
    match = re.match(r'#define\s+FORMS_START\s+SPECIES_(\w+)', line)
    if match:
        forms_start_species = f'SPECIES_{match.group(1)}'
        forms_start = species_map.get(forms_start_species)
        if forms_start:
            print(f"Found FORMS_START = {forms_start}", file=sys.stderr)
        break

# Second pass: evaluate FORMS_START + X expressions
if forms_start:
    for line in lines:
        match = re.match(r'#define\s+SPECIES_(\w+)\s+FORMS_START\s*\+\s*(\d+)', line)
        if match:
            name = match.group(1)
            offset = int(match.group(2))
            species_id = forms_start + offset
            species_map[f'SPECIES_{name}'] = species_id
            print(f"  Found form: SPECIES_{name} = {species_id}", file=sys.stderr)

print(f"\nLoaded {len(species_map)} species total", file=sys.stderr)

# Parse species info files
species_abilities = {}
species_types = {}

gen_files = [
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_1.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_2.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_3.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_4.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_5.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_6.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_7.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_8.h',
    '/Users/samfp/emerogue/src/data/pokemon/species_info/gen_9.h',
]

for gen_file in gen_files:
    try:
        with open(gen_file, 'r') as f:
            lines = f.readlines()

        current_species = None
        for i, line in enumerate(lines):
            # Match species declaration
            species_match = re.match(r'\s*\[SPECIES_(\w+)\]\s*=', line)
            if species_match:
                current_species = species_match.group(1)
                continue

            if current_species:
                # Match abilities line
                abilities_match = re.search(r'\.abilities\s*=\s*\{\s*([^}]+)\}', line)
                if abilities_match:
                    abilities_line = abilities_match.group(1)
                    ability_tokens = [a.strip() for a in abilities_line.split(',')]

                    ability1 = abilities.get(ability_tokens[0], 0) if len(ability_tokens) > 0 else 0
                    ability2 = abilities.get(ability_tokens[1], 0) if len(ability_tokens) > 1 else 0
                    hidden = abilities.get(ability_tokens[2], 0) if len(ability_tokens) > 2 else 0

                    species_key = f'SPECIES_{current_species}'
                    if species_key in species_map:
                        species_id = species_map[species_key]
                        species_abilities[species_id] = (ability1, ability2, hidden)

                # Match types line
                types_match = re.search(r'\.types\s*=\s*\{\s*TYPE_(\w+)\s*,\s*TYPE_(\w+)\s*\}', line)
                if types_match:
                    type1_name = types_match.group(1)
                    type2_name = types_match.group(2)

                    # Map type names to IDs (standard Pokemon type ordering)
                    type_ids = {
                        'NORMAL': 0, 'FIGHTING': 1, 'FLYING': 2, 'POISON': 3,
                        'GROUND': 4, 'ROCK': 5, 'BUG': 6, 'GHOST': 7,
                        'STEEL': 8, 'FIRE': 9, 'WATER': 10, 'GRASS': 11,
                        'ELECTRIC': 12, 'PSYCHIC': 13, 'ICE': 14, 'DRAGON': 15,
                        'DARK': 16, 'FAIRY': 17
                    }

                    type1 = type_ids.get(type1_name, 0)
                    type2 = type_ids.get(type2_name, 0)

                    species_key = f'SPECIES_{current_species}'
                    if species_key in species_map:
                        species_id = species_map[species_key]
                        # Store unique types only
                        if type1 == type2:
                            species_types[species_id] = [type1]
                        else:
                            species_types[species_id] = [type1, type2]

                    current_species = None  # Reset after finding types

    except FileNotFoundError:
        print(f"Warning: {gen_file} not found", file=sys.stderr)

print(f"\nExtracted abilities for {len(species_abilities)} species", file=sys.stderr)
print(f"Extracted types for {len(species_types)} species", file=sys.stderr)

# Output format selector
output_type = sys.argv[1] if len(sys.argv) > 1 else "abilities"

if output_type == "types":
    # Generate types Kotlin code
    print("// Add to SPECIES_TYPES map in PokemonData.kt")
    for species_id in sorted(species_types.keys()):
        if species_id > 905:  # Only output forms
            types = species_types[species_id]
            print(f"        {species_id} to listOf({', '.join(map(str, types))}),")
else:
    # Generate abilities Kotlin code (default)
    print("// Add to ABILITIES map in SpeciesAbilities.kt")
    for species_id in sorted(species_abilities.keys()):
        if species_id > 905:  # Only output forms
            ability1, ability2, hidden = species_abilities[species_id]
            print(f"        {species_id} to Triple({ability1}, {ability2}, {hidden}),")
