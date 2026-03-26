#!/usr/bin/env python3
"""
Extract ability names from Emerald Rogue source
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

# Parse ability names
ability_names = {}
with open('/Users/samfp/emerogue/src/data/text/abilities.h', 'r') as f:
    content = f.read()
    # Find all ability name entries like: [ABILITY_XXX] = _("Name"),
    pattern = r'\[ABILITY_(\w+)\]\s*=\s*_\("([^"]+)"\)'
    for match in re.finditer(pattern, content):
        ability_const = match.group(1)
        ability_name = match.group(2)
        ability_key = f'ABILITY_{ability_const}'
        if ability_key in abilities:
            ability_id = abilities[ability_key]
            ability_names[ability_id] = ability_name
            if ability_id in [29, 61, 62, 99]:  # Debug specific abilities
                print(f"  {ability_key} ({ability_id}): {ability_name}", file=sys.stderr)

print(f"\nExtracted {len(ability_names)} ability names", file=sys.stderr)

# Generate Kotlin code
print("package com.ercompanion.data")
print()
print("/**")
print(" * All ability names extracted from Emerald Rogue source")
print(f" * Total abilities: {len(ability_names)}")
print(" */")
print("object AbilityNames {")
print("    private val NAMES = mapOf(")

for ability_id in sorted(ability_names.keys()):
    name = ability_names[ability_id]
    # Escape quotes in name
    name_escaped = name.replace('"', '\\"')
    print(f'        {ability_id} to "{name_escaped}",')

print("    )")
print()
print("    fun getName(abilityId: Int): String {")
print('        return NAMES[abilityId] ?: "Unknown #$abilityId"')
print("    }")
print("}")
