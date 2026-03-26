# ER Companion - TODO List

**Last Updated:** 2026-03-26

---

## 🔴 CRITICAL PRIORITY

### 1. Critical Hit Support
**Status:** Not implemented
**Impact:** HIGH - Many abilities, items, and moves depend on crits

**What needs to be done:**
- [ ] Implement critical hit calculation in DamageCalculator.kt
- [ ] Critical hit formula: Gen 6+ uses 1.5x multiplier (not 2x)
- [ ] Crit stages 0-4 with formula: chance = (1 + stage) / 24 (Gen 6+)
- [ ] High crit ratio moves (Slash, Crabhammer, etc.) start at stage +1
- [ ] Focus Energy / Dire Hit add +2 stages
- [ ] Items that boost crit: Scope Lens (+1), Razor Claw (+1)
- [ ] Abilities: Super Luck (+1), Sniper (3x damage on crit)
- [ ] Moves that always crit: Frost Breath, Storm Throw, etc.
- [ ] Abilities that block crits: Battle Armor, Shell Armor
- [ ] Crit ignores negative attack stages and positive defense stages

**Interactions with curses:**
- [ ] Crit Curse: Enemy crit chance +10% per curse (max 90%)
- [ ] Currently Crit Curse does nothing - needs crit system first

**Files to modify:**
- DamageCalculator.kt - Add crit calculation
- CurseEffects.kt - Already has getEnemyCritBoost()
- MoveData or move database - Flag high-crit moves

**References:**
- Bulbapedia: Critical hit mechanics Gen 6+
- emerogue/src/battle_util.c - Crit calculation code

---

## 🟡 HIGH PRIORITY

### 2. In-Game Validation Testing
**Status:** Not done
**Impact:** HIGH - We have zero confidence in real accuracy

**Test cases:**
- [ ] Common damage calculations (100+ examples)
- [ ] Verify type chart matches (all 18 types)
- [ ] Verify STAB multiplier (1.5x)
- [ ] Verify weather multipliers (Sun/Rain 1.5x)
- [ ] Verify terrain multipliers (1.3x in Gen 8+)
- [ ] Test each curse effect individually
- [ ] Test combined curse effects
- [ ] Verify AI move predictions (BattleAISimulator)
- [ ] Verify stat stage multipliers

**Create test scenarios document with:**
- Pokemon species, level, stats
- Move used
- Expected damage range
- Actual damage range
- % error

### 3. Fix Remaining Critical Bugs (8 issues)
**Status:** Not fixed
**Impact:** HIGH - Could cause crashes or incorrect results

**From CODE_REVIEW_ISSUES.md:**
- [ ] 1.1: Integer overflow in damage calculation (coerceAtMost 65535)
- [ ] 1.2: Transposition table thread safety (use ConcurrentHashMap)
- [ ] 1.3: Missing null check on MoveData (validate moveId > 0)
- [ ] 1.8: State hash collision risk (hash only battle-relevant fields)
- [ ] 1.10: Memory leak - transposition table (clear in finally block)
- [ ] 1.11: Focus Sash check consistency (validate wasFullHP vs currentHp)
- [ ] 1.12: Weather enum ordinal used directly (pass Weather enum, not Int)
- [ ] 1.6: Infinite loop potential in random rollout (add no-progress counter)

### 4. Implement Remaining Curses (5 curses)
**Status:** Partially implemented (6/11 complete)
**Impact:** MEDIUM - Missing curse features

**Not implemented:**
- [ ] **Crit Curse** - Requires critical hit support first (see #1)
- [ ] **Endure Curse** - Enemy survives with 1 HP, 20% chance per curse
  - Add check in damage application
  - Roll when damage would KO
  - Set HP to 1 if successful
- [ ] **Priority Curse** - Enemy moves gain +1 priority, 10% chance per curse
  - Affects turn order calculation
  - Not critical for damage calcs
  - Low priority
- [ ] **Flinch Curse** - Enemy flinch chance +10% per curse
  - Affects move-by-move simulation
  - Would need flinch move database
  - Medium priority
- [ ] **Pressure Curse** - Player moves cost +1 PP
  - Not relevant for battle calculator (no PP tracking)
  - Very low priority

**Priority order:**
1. Endure Curse (affects KO predictions)
2. Flinch Curse (affects strategy)
3. Priority Curse (affects turn order)
4. Pressure Curse (not relevant)
5. Crit Curse (blocked on #1)

### 5. Multi-Hit Move Support
**Status:** Not implemented
**Impact:** MEDIUM - Affects many popular moves

**Multi-hit moves:**
- Fixed 2-hit: Double Kick, Bonemerang, Dual Chop
- Fixed 3-hit: Triple Kick, Triple Axel
- 2-5 hit: Fury Attack, Pin Missile, Rock Blast, Icicle Spear, Bullet Seed, Tail Slap, Scale Shot
- Hit until miss: Triple Kick

**Implementation:**
- [ ] Add multi-hit field to MoveData
- [ ] Calculate damage per hit
- [ ] Gen 5+ distribution: 2-hit=35%, 3-hit=35%, 4-hit=15%, 5-hit=15%
- [ ] Skill Link ability: always 5 hits
- [ ] Each hit can trigger item effects (King's Rock flinch, etc.)
- [ ] Endure/Focus Sash only trigger on FIRST hit damage
- [ ] Display as range: "50-125 damage (2-5 hits)"

---

## 🟢 MEDIUM PRIORITY

### 6. Confusion Implementation
**Status:** Not implemented
**Impact:** MEDIUM - Some moves/abilities inflict confusion

**What needs to be done:**
- [ ] Add confusion to StatusConditions (separate from status1)
- [ ] Confusion lasts 1-4 turns (Gen 7+: 33% self-hit chance)
- [ ] Self-hit damage: 40 base power typeless physical move
- [ ] Moves that confuse: Confuse Ray, Supersonic, Swagger, etc.
- [ ] Items that cure: Persim Berry, Lum Berry
- [ ] Abilities: Own Tempo (immune), Tangled Feet (boost evasion)

### 7. Fixed Damage Moves
**Status:** Not implemented
**Impact:** MEDIUM - Several popular moves

**Fixed damage moves:**
- [ ] Seismic Toss, Night Shade: Damage = user's level
- [ ] Dragon Rage: 40 damage
- [ ] Sonic Boom: 20 damage
- [ ] Psywave: Random 0.5x to 1.5x user's level
- [ ] Super Fang: Half target's current HP
- [ ] OHKO moves: Fissure, Horn Drill, Guillotine, Sheer Cold

**Implementation:**
- Flag these moves in move database
- Skip normal damage calculation
- Apply fixed formula

### 8. Recoil Damage Support
**Status:** Not implemented
**Impact:** MEDIUM - Popular moves like Brave Bird

**Recoil moves:**
- 25% recoil: Brave Bird, Flare Blitz, Wood Hammer, Head Smash, Double-Edge, Volt Tackle
- 33% recoil: Take Down, Submission, Wild Charge
- 50% recoil: Head Charge
- Crash damage: Jump Kick, High Jump Kick (50% max HP if miss)

**Implementation:**
- [ ] Add recoil percent to MoveData
- [ ] Calculate recoil after damage dealt
- [ ] Rock Head ability: No recoil damage
- [ ] Magic Guard ability: No recoil damage
- [ ] Display: "100 damage dealt, 25 recoil"

### 9. Ability Database Audit
**Status:** Incomplete
**Impact:** MEDIUM - Missing abilities may cause errors

**Check AbilityData.grantsImmunity() covers:**
- [x] Levitate (Ground immunity) ✓
- [x] Volt Absorb (Electric immunity) ✓
- [x] Water Absorb (Water immunity) ✓
- [x] Flash Fire (Fire immunity) ✓
- [x] Sap Sipper (Grass immunity) ✓
- [x] Lightning Rod (Electric immunity) ✓
- [x] Storm Drain (Water immunity) ✓
- [ ] Motor Drive (Electric immunity)
- [ ] Dry Skin (Fire weakness, Water immunity)
- [ ] Thick Fat (Fire/Ice resistance - not immunity)

### 10. Move Database Completeness
**Status:** Unknown
**Impact:** MEDIUM - Missing moves cause errors

**Audit needed:**
- [ ] Verify all 847+ moves have data
- [ ] Check for missing move types
- [ ] Verify move categories (Physical/Special/Status)
- [ ] Check move power values
- [ ] Verify accuracy values
- [ ] Check for missing move effects

### 11. Weather Duration Tracking
**Status:** Not implemented
**Impact:** LOW - Battle calculator doesn't simulate weather expiring

**Implementation:**
- [ ] Add weather duration to BattleState
- [ ] Base: 5 turns
- [ ] With rock (Heat/Damp/Smooth/Icy Rock): 8 turns
- [ ] Infinite with ability (Drought/Drizzle/etc.) in Gen 5
- [ ] 5 turns with ability in Gen 6+
- [ ] Decrement each turn
- [ ] Remove weather when expires

---

## 🔵 LOW PRIORITY

### 12. Accuracy & Evasion System
**Status:** Not implemented
**Impact:** LOW - Most predictions assume moves hit

**Implementation:**
- [ ] Accuracy stages -6 to +6
- [ ] Evasion stages -6 to +6
- [ ] Hit chance = (Accuracy × AccMod) / (EvaMod × 100)
- [ ] Stage multipliers: 3/3, 3/4, 3/5, 3/6, 3/7, 3/8, 3/9
- [ ] Moves that never miss: Aerial Ace, Aura Sphere, etc.
- [ ] Abilities: Keen Eye (ignore evasion), Compound Eyes (+30% acc)

### 13. Two-Turn Moves
**Status:** Not implemented
**Impact:** LOW - Uncommon in AI battles

**Two-turn moves:**
- Fly, Dig, Dive, Bounce, Shadow Force, Phantom Force
- Solar Beam (1 turn in sun)
- Sky Attack
- Razor Wind, Skull Bash, etc.

**Power Herb interaction:**
- Consumes item to skip charge turn

### 14. Form Changes
**Status:** Not implemented
**Impact:** LOW - Rare scenarios

**Form-changing Pokemon:**
- Castform (weather forms)
- Cherrim (Sunshine form in sun)
- Aegislash (Stance Change)
- Darmanitan (Zen Mode)
- Meloetta (Relic Song)

### 15. Mega Evolution Support
**Status:** Unknown if in Emerald Rogue
**Impact:** LOW/UNKNOWN

**Check:**
- [ ] Does ER support Mega Evolution?
- [ ] If yes, implement stat changes on Mega
- [ ] Mega stones in item database
- [ ] Type changes (Mega Charizard X → Fire/Dragon)

### 16. Z-Moves Support
**Status:** Unknown if in Emerald Rogue
**Impact:** LOW/UNKNOWN

**Check:**
- [ ] Does ER support Z-Moves?
- [ ] If yes, implement Z-Move power calculation
- [ ] Z-Status moves effects

---

## 📝 CODE QUALITY IMPROVEMENTS

### 17. Fix High Priority Code Review Issues (18 issues)
**From CODE_REVIEW_ISSUES.md:**
- [ ] 2.1: No validation for move ID range (filter 1..MAX_MOVE_ID)
- [ ] 2.4: No max damage cap (coerce to 65535)
- [x] 2.5: Paralysis mechanics (Gen 7 = 50%) ✓ FIXED
- [ ] 2.9: Download ability comparison
- [ ] 2.10: Shed Skin uses random (make deterministic)
- [ ] 2.11: Ability immunity checks
- [ ] 2.13: Sandstorm Rock type checks
- [ ] 2.14: Terrain grounded checks
- [ ] And 10 more...

### 18. Fix Medium Priority Issues (11 issues)
**Performance optimizations:**
- [x] 3.3: Type effectiveness caching ✓ FIXED
- [x] 3.5: Damage calculation caching ✓ FIXED
- [ ] 3.2: Redundant state copies (builder pattern)
- [ ] 3.4: Deep analysis object allocation
- [ ] 3.6: Setup analysis enemy stat drops
- [ ] And 6 more...

### 19. Unit Test Coverage
**Status:** ~40% coverage
**Target:** 80%+ coverage

**Test gaps:**
- [ ] Critical hit mechanics (when implemented)
- [ ] Multi-hit moves (when implemented)
- [ ] Confusion (when implemented)
- [ ] Fixed damage moves (when implemented)
- [ ] Recoil damage (when implemented)
- [ ] Accuracy/evasion (when implemented)
- [ ] All 11 curse effects (only 6 tested)
- [ ] Complex ability interactions
- [ ] Edge cases (0 HP, overflow, etc.)

### 20. Performance Optimization
**Status:** Not profiled
**Impact:** LOW - Probably fast enough

**Profile and optimize:**
- [ ] Minimax search performance
- [ ] Monte Carlo simulation speed
- [ ] State copy performance
- [ ] Memory allocation patterns
- [ ] Cache hit rates

---

## 🎨 UI/UX IMPROVEMENTS

### 21. Curse Preset Profiles
**Status:** Explicitly NOT wanted by user
**Impact:** N/A

~~Add preset profiles (Easy/Hard/Extreme)~~
User wants manual toggles only.

### 22. Battle Log/History
**Status:** Not implemented
**Impact:** LOW - Nice to have

**Features:**
- [ ] Show last 5-10 moves
- [ ] Show damage dealt/taken
- [ ] Show status changes
- [ ] Export battle log

### 23. Stat Calculator
**Status:** Not implemented
**Impact:** LOW - Useful tool

**Show calculated stats at level:**
- [ ] Base stats
- [ ] IVs
- [ ] EVs
- [ ] Nature
- [ ] Stat stages
- [ ] Final stats

### 24. Type Coverage Analyzer
**Status:** Not implemented
**Impact:** LOW - Team building tool

**Analyze team type coverage:**
- [ ] Offensive coverage
- [ ] Defensive coverage
- [ ] Weaknesses
- [ ] Resistances

---

## 📚 DOCUMENTATION

### 25. User Guide
**Status:** Not written
**Impact:** MEDIUM - Users need documentation

**Sections needed:**
- [ ] How to use ER Companion
- [ ] Memory address scanning
- [ ] Curse system explanation
- [ ] Optimal line interpretation
- [ ] Troubleshooting

### 26. Developer Documentation
**Status:** Minimal
**Impact:** LOW - Future maintenance

**Sections needed:**
- [ ] Architecture overview
- [ ] How to add new abilities
- [ ] How to add new moves
- [ ] How to add new items
- [ ] Testing guide

### 27. Changelog
**Status:** Not maintained
**Impact:** LOW

**Track:**
- [ ] Feature additions
- [ ] Bug fixes
- [ ] Breaking changes
- [ ] Performance improvements

---

## 🔧 INFRASTRUCTURE

### 28. CI/CD Pipeline
**Status:** Not set up
**Impact:** LOW - Manual builds work

**Set up:**
- [ ] GitHub Actions for builds
- [ ] Automated testing on PR
- [ ] APK artifact publishing
- [ ] Release tagging

### 29. Crash Reporting
**Status:** Not implemented
**Impact:** MEDIUM - Need user feedback

**Options:**
- [ ] Firebase Crashlytics
- [ ] Sentry
- [ ] Custom logging

### 30. Analytics (Optional)
**Status:** Not implemented
**Impact:** LOW - Privacy concerns

**Track:**
- [ ] Feature usage
- [ ] Curse usage patterns
- [ ] Performance metrics
- [ ] Crash rates

---

## 📊 SUMMARY

**Total Items:** 30
**Critical:** 1 (Crit support)
**High Priority:** 9 (Validation, bugs, curses, multi-hit)
**Medium Priority:** 10 (Confusion, fixed damage, recoil, etc.)
**Low Priority:** 10 (Accuracy, two-turn, forms, etc.)

**Estimated Time:**
- Critical: 2-3 days
- High Priority: 5-7 days
- Medium Priority: 7-10 days
- Low Priority: 10-15 days
- **Total:** ~4-6 weeks for complete implementation

**Next Steps (Recommended Order):**
1. ✅ Build and publish APKs
2. ✅ Push source code
3. In-game validation testing (1-2 days)
4. Critical hit support (2-3 days)
5. Fix 8 critical bugs (1-2 days)
6. Implement Endure curse (4 hours)
7. Multi-hit move support (1 day)
8. Continue down priority list...
