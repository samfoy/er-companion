# Deep Analysis Mode - Usage Guide

## Overview

Deep Analysis Mode provides optional thorough battle analysis that can take 1-5 seconds to produce extremely comprehensive battle strategies. This addresses the user feedback: "if there is a type of analysis that would take a long time, that is an option too. pokemon is turn based, its ok if it needs to cook for a while."

## Features

### 1. Analysis Depth Levels

Four preset depth levels are available:

- **QUICK** (< 200ms): Depth 3, no Monte Carlo, no exhaustive analysis
- **STANDARD** (< 500ms): Depth 7, beam search only
- **DEEP** (1-2s): Depth 10, 50 Monte Carlo samples, exhaustive setup analysis
- **EXHAUSTIVE** (3-5s): Depth 10, 100 Monte Carlo samples, switching analysis, exhaustive setup

### 2. Monte Carlo Simulation

Runs random game rollouts to estimate win probability:

```kotlin
val monteCarloResult = report.monteCarloResult
// Results:
// - winRate: 0.73 (73% win rate)
// - avgTurnsToWin: 4.2 turns average
// - avgTurnsToLose: 2.8 turns average
// - sampleCount: 100 simulations
```

### 3. Switching Analysis

Evaluates all party members as potential switches:

```kotlin
val recommendations = report.switchRecommendations
// Example output:
// 1. Switch to Blissey (defensive pivot)
//    Score: 8.5/10
//    Reasoning: "Switch to Blissey: Seismic Toss (3HKO)"
```

### 4. Exhaustive Setup Analysis

Tries all setup move combinations (1-3 setup turns):

```kotlin
val strategies = report.setupStrategies
// Example output:
// Best: Dragon Dance × 2 → Outrage
//   - 2 setup turns
//   - KO in 4 total turns
//   - 55% HP remaining
//   - Score: 9.8/10
```

## Basic Usage

### Simple Integration

```kotlin
import com.ercompanion.calc.DeepAnalysisMode
import com.ercompanion.calc.AnalysisDepth

// Perform deep analysis
val report = DeepAnalysisMode.performDeepAnalysis(
    player = activePlayerMon,
    enemy = activeEnemyMon,
    depth = AnalysisDepth.DEEP
)

// Access results
println("Win Rate: ${(report.monteCarloResult?.winRate ?: 0f) * 100}%")
println("Best Line: ${report.optimalLines.firstOrNull()?.description}")
```

### With Progress Callback

```kotlin
val report = DeepAnalysisMode.performDeepAnalysis(
    player = activePlayerMon,
    enemy = activeEnemyMon,
    depth = AnalysisDepth.EXHAUSTIVE,
    onProgress = { message ->
        // Update UI with progress
        println(message)
        // e.g., "Calculating optimal lines..."
        //       "Running Monte Carlo simulations (100 samples)..."
        //       "Analysis complete!"
    }
)
```

### With Full Party (for Switching Analysis)

```kotlin
val report = DeepAnalysisMode.performDeepAnalysis(
    player = activePlayerMon,
    enemy = activeEnemyMon,
    playerParty = fullPlayerParty,  // Include all 6 party members
    depth = AnalysisDepth.EXHAUSTIVE
)

// Check switch recommendations
report.switchRecommendations?.forEach { rec ->
    println("Switch to ${rec.targetMon.nickname}: ${rec.reasoning}")
}
```

### Format Report for Console/Debug

```kotlin
val report = DeepAnalysisMode.performDeepAnalysis(
    player = activePlayerMon,
    enemy = activeEnemyMon,
    depth = AnalysisDepth.DEEP
)

// Pretty-print the report
val formatted = DeepAnalysisMode.formatReport(report)
println(formatted)
```

## UI Integration Example

### ViewModel Integration

```kotlin
// In MainViewModel.kt or CompactBattleViewModel.kt

class BattleViewModel : ViewModel() {
    private val _deepAnalysisReport = MutableStateFlow<DeepAnalysisReport?>(null)
    val deepAnalysisReport: StateFlow<DeepAnalysisReport?> = _deepAnalysisReport

    private val _analysisProgress = MutableStateFlow("")
    val analysisProgress: StateFlow<String> = _analysisProgress

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    fun triggerDeepAnalysis(depth: AnalysisDepth = AnalysisDepth.DEEP) {
        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true

            val report = DeepAnalysisMode.performDeepAnalysis(
                player = activePlayerMon,
                enemy = activeEnemyMon,
                playerParty = playerParty,
                depth = depth,
                onProgress = { message ->
                    _analysisProgress.value = message
                }
            )

            _deepAnalysisReport.value = report
            _isAnalyzing.value = false
        }
    }
}
```

### Composable UI Example

```kotlin
@Composable
fun DeepAnalysisButton(viewModel: BattleViewModel) {
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val progress by viewModel.analysisProgress.collectAsState()

    Column {
        Button(
            onClick = {
                viewModel.triggerDeepAnalysis(AnalysisDepth.DEEP)
            },
            enabled = !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Analyzing..." else "Deep Analysis")
        }

        if (isAnalyzing) {
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DeepAnalysisResults(report: DeepAnalysisReport?) {
    report?.let {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Deep Analysis (${it.analysisTimeMs}ms)",
                 style = MaterialTheme.typography.titleMedium)

            // Monte Carlo Results
            it.monteCarloResult?.let { mc ->
                Card(modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Win Probability: ${(mc.winRate * 100).toInt()}%")
                        if (mc.avgTurnsToWin > 0) {
                            Text("Avg Turns to Win: ${mc.avgTurnsToWin}")
                        }
                    }
                }
            }

            // Optimal Lines
            Text("Best Strategies:", style = MaterialTheme.typography.titleSmall)
            it.optimalLines.take(3).forEach { line ->
                Text("• ${line.description}",
                     modifier = Modifier.padding(start = 8.dp))
            }

            // Switch Recommendations
            it.switchRecommendations?.take(2)?.let { switches ->
                if (switches.isNotEmpty()) {
                    Text("Switch Recommendations:",
                         style = MaterialTheme.typography.titleSmall,
                         modifier = Modifier.padding(top = 8.dp))
                    switches.forEach { rec ->
                        Text("• ${rec.reasoning}",
                             modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
```

## Performance Characteristics

### Timing Expectations

| Depth | Time | Depth | Monte Carlo | Switching | Setup |
|-------|------|-------|-------------|-----------|-------|
| QUICK | <200ms | 3 | No | No | No |
| STANDARD | <500ms | 7 | No | No | No |
| DEEP | 1-2s | 10 | 50 samples | No | Yes |
| EXHAUSTIVE | 3-5s | 10 | 100 samples | Yes | Yes |

### When to Use Each Depth

- **QUICK**: Real-time suggestions during battle (existing behavior)
- **STANDARD**: Quick preview of 7-turn strategies
- **DEEP**: Detailed analysis when planning next move
- **EXHAUSTIVE**: Between battles or when making critical decisions

## Advanced Configuration

### Custom Configuration

```kotlin
val customConfig = DeepAnalysisConfig(
    maxDepth = 12,              // Look 12 turns ahead
    beamWidth = 4,              // Keep top 4 moves at each level
    monteCarloSamples = 200,    // 200 random simulations
    considerSwitching = true,   // Evaluate switching
    exhaustiveSetup = true,     // Try all setup combos
    maxTimeMs = 10000           // 10 second timeout
)

// Note: Custom config requires direct access to internal functions
// For production use, stick to AnalysisDepth presets
```

## Example Output

```
=== DEEP ANALYSIS REPORT (4823ms) ===
Depth: DEEP

=== OPTIMAL LINES ===
1. Dragon Dance → Dragon Dance → Outrage (3HKO)
   Score: 9.8/10, Survival: 55%
2. Swords Dance → Close Combat → Close Combat (3HKO)
   Score: 9.2/10, Survival: 35%
3. Outrage → Outrage → Outrage (3HKO)
   Score: 8.5/10, Survival: 45%

=== MONTE CARLO SIMULATION (50 samples) ===
Win Rate: 73%
Avg Turns to Win: 4
Avg Turns to Loss: 3

=== EXHAUSTIVE SETUP ANALYSIS ===
Best: Dragon Dance x2 -> Outrage
  - 2 setup turns
  - KO in 4 total turns
  - 55% HP remaining
  - Score: 9.8/10
```

## Implementation Notes

1. **Threading**: Always run deep analysis on a background thread (Dispatchers.Default)
2. **Cancellation**: Consider adding timeout handling for EXHAUSTIVE mode
3. **Caching**: Results can be cached if battle state hasn't changed
4. **UI Feedback**: Always show progress for DEEP/EXHAUSTIVE modes (1-5 seconds)
5. **Error Handling**: Handle cases where no valid moves exist

## Files

- Implementation: `/app/src/main/java/com/ercompanion/calc/DeepAnalysisMode.kt`
- Tests: `/app/src/test/java/com/ercompanion/calc/DeepAnalysisModeTest.kt`
- This guide: `/DEEP_ANALYSIS_USAGE.md`
