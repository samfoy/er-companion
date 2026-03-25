package com.ercompanion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.ercompanion.MainViewModel
import com.ercompanion.data.MoveData
import com.ercompanion.data.PokemonData
import com.ercompanion.network.RetroArchClient
import com.ercompanion.parser.PartyMon
import com.ercompanion.ui.theme.HPGreen
import com.ercompanion.ui.theme.HPRed
import com.ercompanion.ui.theme.HPYellow
import com.ercompanion.utils.SpriteUtils
import com.ercompanion.utils.TopHalfCropTransformation

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    connectionState: RetroArchClient.ConnectionStatus,
    partyState: List<PartyMon?>,
    enemyPartyState: List<PartyMon?>,
    activePlayerSlot: Int,
    scanningState: Boolean,
    errorMessage: String?,
    debugLog: List<String>,
    onRescan: () -> Unit
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header with connection status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ER Companion",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                RetroArchClient.ConnectionStatus.CONNECTED -> HPGreen
                                RetroArchClient.ConnectionStatus.DISCONNECTED -> Color.Gray
                                RetroArchClient.ConnectionStatus.ERROR -> HPRed
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (connectionState) {
                        RetroArchClient.ConnectionStatus.CONNECTED -> "Connected"
                        RetroArchClient.ConnectionStatus.DISCONNECTED -> "Disconnected"
                        RetroArchClient.ConnectionStatus.ERROR -> "Error"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = onRescan,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("↺ Rescan", fontSize = 12.sp, color = Color(0xFF888888))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error or status message
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!scanningState) {
                        TextButton(onClick = onRescan) {
                            Text("Rescan")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Debug panel
        DebugPanel(
            debugLog = debugLog,
            currentManualPath = viewModel.debugManualPath,
            saveStateStatus = viewModel.getSaveStateStatus(),
            searchPaths = viewModel.getSaveStateSearchPaths(),
            onApply = { path -> viewModel.applySaveStatePath(path) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val enemyLead = enemyPartyState.firstOrNull()
        val inBattle = enemyLead != null

        if (partyState.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (scanningState) "Scanning..." else "No party data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else if (inBattle && enemyLead != null) {
            // ── BATTLE LAYOUT: two-column ──────────────────────────────────────────
            val activeMon = if (activePlayerSlot >= 0) partyState.getOrNull(activePlayerSlot)
                            else partyState.firstOrNull { it != null }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left column: our team — active mon expanded, bench collapsed to chips
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "YOUR TEAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    partyState.forEachIndexed { index, mon ->
                        if (mon != null) {
                            val isActive = index == activePlayerSlot
                            if (isActive) {
                                PokemonCard(
                                    viewModel = viewModel,
                                    mon = mon,
                                    slotNumber = index + 1,
                                    enemyTarget = enemyLead,
                                    isActive = true,
                                    showAiPrediction = false
                                )
                            } else {
                                BenchedMonChip(mon = mon, enemyTarget = enemyLead)
                            }
                        }
                    }
                }

                // Right column: enemy lead with AI prediction
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENEMY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAA4444),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    EnemyLeadCard(enemyLead = enemyLead, activeMon = activeMon, viewModel = viewModel)
                }
            }
        } else {
            // ── OUT-OF-BATTLE LAYOUT: single column, builds collapsed ──────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                partyState.forEachIndexed { index, mon ->
                    if (mon != null) {
                        PokemonCard(
                            viewModel = viewModel,
                            mon = mon,
                            slotNumber = index + 1,
                            enemyTarget = null,
                            isActive = false,
                            showAiPrediction = false,
                            defaultExpanded = false
                        )
                    }
                }
            }
        }
    }
}

/** Compact chip for benched mons during battle — shows sprite, name, HP bar, damage calc summary */
@Composable
fun BenchedMonChip(mon: PartyMon, enemyTarget: PartyMon?) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f
    val hpColor = when {
        hpFrac > 0.5f -> HPGreen
        hpFrac > 0.2f -> HPYellow
        else -> HPRed
    }
    // Best move damage vs enemy
    val bestDmgPct = if (enemyTarget != null) {
        mon.moves.filter { it != 0 }.mapNotNull { moveId ->
            val md = PokemonData.getMoveData(moveId) ?: return@mapNotNull null
            if (md.power == 0) return@mapNotNull null
            val atkStat = if (md.category == 0) mon.attack else mon.spAttack
            val defStat = if (md.category == 0) enemyTarget.defense else enemyTarget.spDefense
            val result = com.ercompanion.calc.DamageCalculator.calc(
                mon.level, atkStat, defStat, md.power, md.type,
                PokemonData.getSpeciesTypes(mon.species),
                PokemonData.getSpeciesTypes(enemyTarget.species),
                enemyTarget.maxHp
            )
            if (enemyTarget.maxHp > 0) (result.maxDamage * 100 / enemyTarget.maxHp) else 0
        }.maxOrNull()
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ctx = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(SpriteUtils.getSpriteUrl(speciesName))
                    .transformations(TopHalfCropTransformation())
                    .build(),
                contentDescription = speciesName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(speciesName, style = MaterialTheme.typography.labelMedium, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Lv${mon.level}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                }
                // HP bar
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                            .fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(hpColor)
                    )
                }
                if (bestDmgPct != null && bestDmgPct > 0) {
                    val color = when {
                        bestDmgPct >= 100 -> HPRed
                        bestDmgPct >= 50  -> HPYellow
                        else              -> Color.Gray
                    }
                    Text("best move: ${bestDmgPct}%", style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun EnemyLeadCard(enemyLead: PartyMon, activeMon: PartyMon?, viewModel: MainViewModel) {
    val speciesName = PokemonData.getSpeciesName(enemyLead.species)
    val types = PokemonData.getSpeciesTypes(enemyLead.species)
    val typeNames = types.map { typeId ->
        listOf("Normal","Fighting","Flying","Poison","Ground","Rock","Bug","Ghost","Steel",
               "Fire","Water","Grass","Electric","Psychic","Ice","Dragon","Dark","Fairy"
        ).getOrNull(typeId) ?: "?"
    }

    // Score enemy moves vs the active player mon
    val scoredMoves = if (activeMon != null) {
        com.ercompanion.calc.BattleAISimulator.scoreMovesVsTarget(enemyLead, activeMon)
    } else emptyList()
    val predicted = com.ercompanion.calc.BattleAISimulator.predictAiMove(scoredMoves)
    val isRandom = predicted.size > 1
    val playerName = activeMon?.let { PokemonData.getSpeciesName(it.species) } ?: "?"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: sprite + name/type + HP bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ctx = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(SpriteUtils.getSpriteUrl(speciesName))
                        .transformations(TopHalfCropTransformation())
                        .build(),
                    contentDescription = speciesName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚔ $speciesName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(
                        text = "Lv.${enemyLead.level}  ${typeNames.joinToString("/")}  Spd:${enemyLead.speed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${enemyLead.hp}/${enemyLead.maxHp} HP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    val hpFraction = if (enemyLead.maxHp > 0) enemyLead.hp.toFloat() / enemyLead.maxHp else 0f
                    LinearProgressIndicator(
                        progress = hpFraction,
                        modifier = Modifier.width(80.dp).height(6.dp),
                        color = when {
                            hpFraction > 0.5f -> Color(0xFF4CAF50)
                            hpFraction > 0.25f -> Color(0xFFFFEB3B)
                            else -> Color(0xFFF44336)
                        }
                    )
                }
            }

            // AI move prediction
            if (scoredMoves.isNotEmpty() && activeMon != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF3A2A2A))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isRandom) "🎲 AI: random (${predicted.size} tied)" else "🎯 AI will likely use:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Show all moves sorted by score, predicted move(s) highlighted
                scoredMoves.sortedByDescending { it.score }.forEach { sm ->
                    val isPredicted = predicted.contains(sm)
                    val dmgColor = when {
                        sm.damagePercent >= 100 -> Color(0xFFF44336)
                        sm.damagePercent >= 50  -> Color(0xFFFFEB3B)
                        else                    -> Color(0xFFAAAAAA)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isPredicted) "▶ " else "  ",
                                fontSize = 10.sp,
                                color = if (isPredicted) Color(0xFFFF6B6B) else Color.Transparent
                            )
                            Text(
                                text = sm.moveName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isPredicted) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPredicted) Color.White else Color.Gray
                            )
                            if (sm.moveData?.power != null && sm.moveData.power > 0) {
                                Text(
                                    text = " (${sm.moveData.power})",
                                    fontSize = 9.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        }
                        Text(
                            text = if (sm.damagePercent > 0) "${sm.damagePercent}% vs $playerName" else sm.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = dmgColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PokemonCard(viewModel: MainViewModel, mon: PartyMon, slotNumber: Int, enemyTarget: PartyMon? = null, isActive: Boolean = false, showAiPrediction: Boolean = false, defaultExpanded: Boolean = false) {
    var expanded by remember(mon.species) { mutableStateOf(defaultExpanded || isActive) }
    var buildsExpanded by remember(mon.species) { mutableStateOf(false) }
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val pokemonBuild = viewModel.getBuildForSpecies(speciesName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1A2A1A) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Slot number, sprite, name, level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isActive) "▶ $slotNumber" else "#$slotNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) Color(0xFF4CAF50) else Color.Gray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(SpriteUtils.getSpriteUrl(speciesName))
                            .transformations(TopHalfCropTransformation())
                            .build(),
                        contentDescription = speciesName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = speciesName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (pokemonBuild?.tier != null) {
                            Text(
                                text = pokemonBuild.tier,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (pokemonBuild.tier) {
                                    "Z"  -> Color(0xFFFF00FF) // Magenta — legendary
                                    "S+" -> Color(0xFFFF4444) // Bright red
                                    "S"  -> Color(0xFFFFD700) // Gold
                                    "A"  -> Color(0xFF4CAF50) // Green
                                    "B"  -> Color(0xFF2196F3) // Blue
                                    "C"  -> Color(0xFF9E9E9E) // Gray
                                    else -> Color(0xFF666666)
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Lv. ${mon.level}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HP bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HP",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${mon.hp} / ${mon.maxHp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                val hpPercentage = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f
                val hpColor = when {
                    hpPercentage > 0.5f -> HPGreen
                    hpPercentage > 0.2f -> HPYellow
                    else -> HPRed
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hpPercentage.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(hpColor)
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("ATK", mon.attack)
                        StatItem("DEF", mon.defense)
                        StatItem("SPD", mon.speed)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("SP.ATK", mon.spAttack)
                        StatItem("SP.DEF", mon.spDefense)
                        StatItem("EXP", mon.experience)
                    }

                    // Recommended build — collapsed by default
                    if (pokemonBuild != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.DarkGray)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { buildsExpanded = !buildsExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recommended Build",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (buildsExpanded) "▲" else "▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        AnimatedVisibility(visible = buildsExpanded) {
                            Column {
                                if (pokemonBuild.notes != null) {
                                    Text(pokemonBuild.notes, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (pokemonBuild.recommendedMoves.isNotEmpty()) {
                                    Text("Suggested moves:", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.Gray)
                                    Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                                        pokemonBuild.recommendedMoves.forEach { moveName ->
                                            Text("• $moveName", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 1.dp))
                                        }
                                    }
                                }
                                if (pokemonBuild.recommendedItem != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Item: ${pokemonBuild.recommendedItem}", style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    // Moves with damage calculations
                    if (mon.moves.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Moves",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        mon.moves.forEach { moveId ->
                            MoveItem(
                                mon = mon,
                                moveId = moveId,
                                enemyTarget = enemyTarget
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoveItem(mon: PartyMon, moveId: Int, enemyTarget: PartyMon?) {
    val moveName = PokemonData.getMoveName(moveId)
    val moveData = PokemonData.getMoveData(moveId)

    if (moveData == null || moveData.power == 0) {
        // Status move or unknown move
        Text(
            text = "• $moveName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        return
    }

    if (enemyTarget == null) {
        // No enemy to calculate damage against
        Text(
            text = "• $moveName — vs ?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        return
    }

    // Calculate damage
    val attackerTypes = PokemonData.getSpeciesTypes(mon.species)
    val defenderTypes = PokemonData.getSpeciesTypes(enemyTarget.species)

    val attackStat = if (moveData.category == 0) mon.attack else mon.spAttack
    val defenseStat = if (moveData.category == 0) enemyTarget.defense else enemyTarget.spDefense

    val result = com.ercompanion.calc.DamageCalculator.calc(
        attackerLevel = mon.level,
        attackStat = attackStat,
        defenseStat = defenseStat,
        movePower = moveData.power,
        moveType = moveData.type,
        attackerTypes = attackerTypes,
        defenderTypes = defenderTypes,
        targetMaxHP = enemyTarget.maxHp,
        isBurned = false,
        weather = 0,
        moveName = moveName
    )

    // Color based on effectiveness
    val effectColor = when {
        result.effectiveness == 0f -> Color.DarkGray
        result.effectiveness < 1f -> Color.Gray
        result.effectiveness > 1f -> Color(0xFFFF6B6B) // Red for super effective
        else -> MaterialTheme.colorScheme.onSurface
    }

    val damageText = if (result.maxDamage > 0) {
        "${result.minDamage}–${result.maxDamage} dmg (${result.percentMin}–${result.percentMax}%)"
    } else {
        "No damage"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• $moveName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = damageText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = effectColor,
                fontWeight = if (result.effectiveness > 1f) FontWeight.Bold else FontWeight.Normal
            )
            if (result.effectLabel.isNotEmpty()) {
                Text(
                    text = result.effectLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = effectColor
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DebugPanel(
    debugLog: List<String>,
    currentManualPath: String,
    saveStateStatus: String,
    searchPaths: List<String>,
    onApply: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(currentManualPath) }
    var showPaths by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔧 Debug", style = MaterialTheme.typography.labelMedium, color = Color(0xFF888888))
                Text(if (expanded) "▲" else "▼", color = Color(0xFF888888), fontSize = 10.sp)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // Status line
                Text("Status:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = saveStateStatus,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = if (saveStateStatus.startsWith("Reading:")) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Manual path override
                Text("Manual Path Override:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        placeholder = { Text("/path/to/file.state0", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    )
                    Button(
                        onClick = { onApply(pathInput) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Apply", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Searched paths (collapsible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPaths = !showPaths },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Searched Paths:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                    Text(if (showPaths) "▲" else "▼", color = Color(0xFF888888), fontSize = 10.sp)
                }

                if (showPaths) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        searchPaths.forEach { path ->
                            Text(
                                text = "• $path",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Last updated timestamp
                Text("State Files Found:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    if (debugLog.isEmpty()) {
                        Text("Searching...", color = Color(0xFF444444), fontSize = 10.sp)
                    } else {
                        debugLog.forEach { line ->
                            val color = when {
                                line.startsWith("Reading:") || line.startsWith("OK:") -> Color(0xFF6BCB77)
                                line.contains("not found") || line.contains("empty") -> Color(0xFF666666)
                                line.startsWith("No state") || line.contains("fail") || line.contains("Error") -> Color(0xFFFF6B6B)
                                else -> Color(0xFF888888)
                            }
                            Text(line, color = color, fontSize = 9.sp, lineHeight = 13.sp,
                                modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }
}
