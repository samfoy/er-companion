#!/usr/bin/env python3
"""
Extract species abilities from Emerald Rogue source code
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

print(f"Loaded {len(abilities)} abilities", file=sys.stderr)

# Parse species constants
species_map = {}
species_file = '/Users/samfp/emerogue/include/constants/species.h'

with open(species_file, 'r') as f:
    for line in f:
        match = re.match(r'#define\s+SPECIES_(\w+)\s+(\d+)', line)
        if match:
            name, id_num = match.groups()
            species_map[f'SPECIES_{name}'] = int(id_num)

print(f"Loaded {len(species_map)} species", file=sys.stderr)

# Parse species info files - simpler approach
species_abilities = {}

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

            # Match abilities line
            if current_species:
                abilities_match = re.search(r'\.abilities\s*=\s*\{\s*([^}]+)\}', line)
                if abilities_match:
                    abilities_line = abilities_match.group(1)
                    # Parse ability slots - may have 2 or 3 entries
                    ability_tokens = [a.strip() for a in abilities_line.split(',')]

                    # Get ability IDs, defaulting to 0
                    ability1 = abilities.get(ability_tokens[0], 0) if len(ability_tokens) > 0 else 0
                    ability2 = abilities.get(ability_tokens[1], 0) if len(ability_tokens) > 1 else 0
                    hidden = abilities.get(ability_tokens[2], 0) if len(ability_tokens) > 2 else 0

                    species_key = f'SPECIES_{current_species}'
                    if species_key in species_map:
                        species_id = species_map[species_key]
                        species_abilities[species_id] = (ability1, ability2, hidden)
                        if species_id in [246, 247, 248]:  # Debug Larvitar line
                            print(f"  {species_key} ({species_id}): {ability1}, {ability2}, {hidden}", file=sys.stderr)

                    current_species = None  # Reset after finding abilities

    except FileNotFoundError:
        print(f"Warning: {gen_file} not found", file=sys.stderr)

print(f"\nExtracted abilities for {len(species_abilities)} species", file=sys.stderr)

# Generate Kotlin code
print("package com.ercompanion.data")
print()
print("/**")
print(" * Species ability data extracted from Emerald Rogue source")
print(" * Format: species ID -> Triple(ability1, ability2, hiddenAbility)")
print(f" * Total species: {len(species_abilities)}")
print(" */")
print("object SpeciesAbilities {")
print("    private val ABILITIES = mapOf(")

for species_id in sorted(species_abilities.keys()):
    ability1, ability2, hidden = species_abilities[species_id]
    print(f"        {species_id} to Triple({ability1}, {ability2}, {hidden}),")

print("    )")
print()
print("    /**")
print("     * Get ability ID for a species and ability slot")
print("     * @param speciesId Pokemon species ID")
print("     * @param slot 0=ability1, 1=ability2, 2=hidden ability")
print("     * @return ability ID, or 0 if not found")
print("     */")
print("    fun getAbility(speciesId: Int, slot: Int): Int {")
print("        val abilities = ABILITIES[speciesId] ?: return 0")
print("        return when (slot) {")
print("            0 -> abilities.first")
print("            1 -> abilities.second")
print("            2 -> abilities.third")
print("            else -> 0")
print("        }")
print("    }")
print("}")
