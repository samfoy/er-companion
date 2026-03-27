# Task: Wire Up Optimal Battle Line Prediction in MainScreen

The optimal line calculator is fully built but never shown in the UI. Your job is to integrate it into the main battle view.

## Context
- Project: ~/Projects/er-companion
- The engine lives in: `app/src/main/java/com/ercompanion/calc/OptimalLineCalculator.kt`
- The README is at: `app/src/main/java/com/ercompanion/calc/README_BATTLE_LINES.md` — read it first
- The main UI is: `app/src/main/java/com/ercompanion/ui/MainScreen.kt`
- The enemy card composable is: `EnemyLeadCard` in MainScreen.kt

## What to Build

Add an **"Optimal Line" section** to the battle view that shows the top 2-3 recommended move sequences for the active player mon vs the active enemy.

### Where to Add It
In `MainScreen.kt`, in the battle layout (the two-column section when `inBattle == true`). Add a collapsible card BELOW the two-column player/enemy layout, spanning full width. Title: "⚔ Optimal Line" or similar.

### What to Show
Call `OptimalLineCalculator.calculateOptimalLines(player = activeMon, enemy = enemyLead, maxDepth = 2, topN = 3)` and display results like:

```
⚔ OPTIMAL LINE
────────────────────────────────
▶ Dragon Dance → Outrage         9.2/10  ✓ 2HKO  90% surv
  Close Combat → Close Combat    8.5/10  ✓ 2HKO  70% surv
  Swords Dance → Close Combat⚠  6.8/10  ✓ 2HKO  30% surv
```

### Implementation Notes
1. Use `remember(activeMon?.species, enemyLead?.species, activeMon?.moves, enemyLead?.moves)` to cache the calculation
2. Run it on a background thread if needed (LaunchedEffect) — target <100ms
3. Color code: score >= 8 = green, 6-8 = yellow, < 6 = red/orange
4. ⚠ icon on risky lines (survival < 40%)
5. Show "No setup available" if all lines are direct attacks (no setup moves)
6. Make it collapsible (collapsed by default) with a tap to expand

### Style Constraints
- Match the existing dark theme (background: Color(0xFF1A1A2A) or similar)
- Use `MaterialTheme.typography.labelSmall` for move names
- Keep it compact — this is phone UI

## Also Fix
In `BattleAISimulator.scoreMovesVsTarget`, the `DamageCalculator.calc()` call is missing `moveCategory = moveData.category`. Add it.

## Build & Verify
After changes, run: `./gradlew assembleDebug`
Must compile clean. Fix any errors.

Commit with: "feat: wire up optimal battle line prediction in main battle view"
Then run: openclaw system event --text "Done: Optimal line prediction wired into main battle view" --mode now
