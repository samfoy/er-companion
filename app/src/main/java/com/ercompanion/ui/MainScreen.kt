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
import com.ercompanion.MainViewModel
import com.ercompanion.data.MoveData
import com.ercompanion.data.PokemonData
import com.ercompanion.network.RetroArchClient
import com.ercompanion.parser.PartyMon
import com.ercompanion.ui.theme.HPGreen
import com.ercompanion.ui.theme.HPRed
import com.ercompanion.ui.theme.HPYellow
import com.ercompanion.utils.SpriteUtils

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    connectionState: RetroArchClient.ConnectionStatus,
    partyState: List<PartyMon?>,
    enemyPartyState: List<PartyMon?>,
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

        // Enemy lead card (slot 7 from save state = active enemy during battle)
        val enemyLead = enemyPartyState.firstOrNull()
        if (enemyLead != null) {
            EnemyLeadCard(enemyLead = enemyLead, playerParty = partyState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Party display
        if (partyState.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (scanningState) "Scanning..." else "No party data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                partyState.forEachIndexed { index, mon ->
                    if (mon != null) {
                        PokemonCard(viewModel = viewModel, mon = mon, slotNumber = index + 1, enemyTarget = enemyLead)
                    }
                }
            }
        }
    }
}

@Composable
fun EnemyLeadCard(enemyLead: PartyMon, playerParty: List<PartyMon?>, viewModel: MainViewModel) {
    val speciesName = PokemonData.getSpeciesName(enemyLead.species)
    val types = PokemonData.getSpeciesTypes(enemyLead.species)
    val typeNames = types.map { typeId ->
        listOf("Normal","Fighting","Flying","Poison","Ground","Rock","Bug","Ghost","Steel",
               "Fire","Water","Grass","Electric","Psychic","Ice","Dragon","Dark","Fairy"
        ).getOrNull(typeId) ?: "?"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = SpriteUtils.getSpriteUrl(speciesName),
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
                        text = "Lv.${enemyLead.level}  ${typeNames.joinToString("/")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                // HP bar
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

            // Enemy moves
            if (enemyLead.moves.any { it != 0 }) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Moves:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    enemyLead.moves.filter { it != 0 }.forEach { moveId ->
                        val moveData = PokemonData.getMoveData(moveId)
                        val moveName = PokemonData.getMoveName(moveId)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF3A2A2A)
                        ) {
                            Text(
                                text = if (moveData != null && moveData.power > 0) "$moveName(${moveData.power})" else moveName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = Color(0xFFFFAA88),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // "Can it OHKO me?" — best damage enemy can deal vs each of my mons
            val playerLead = playerParty.firstOrNull { it != null }
            if (playerLead != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF3A2A2A))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Threat vs your lead:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                val bestEnemyDmg = enemyLead.moves.filter { it != 0 }.mapNotNull { moveId ->
                    PokemonData.getMoveData(moveId)?.let { moveData ->
                        if (moveData.power > 0) {
                            val dmg = viewModel.calcDamage(enemyLead, playerLead, moveData)
                            Triple(moveId, moveData, dmg)
                        } else null
                    }
                }.maxByOrNull { it.third }

                if (bestEnemyDmg != null) {
                    val (moveId, moveData, dmg) = bestEnemyDmg
                    val pct = if (playerLead.maxHp > 0) (dmg * 100 / playerLead.maxHp) else 0
                    val moveName = PokemonData.getMoveName(moveId)
                    val color = when {
                        pct >= 100 -> Color(0xFFF44336)
                        pct >= 50 -> Color(0xFFFFEB3B)
                        else -> Color(0xFF4CAF50)
                    }
                    Text(
                        text = "${PokemonData.getSpeciesName(playerLead.species)}: $moveName → $dmg dmg ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun PokemonCard(viewModel: MainViewModel, mon: PartyMon, slotNumber: Int, enemyTarget: PartyMon? = null) {
    var expanded by remember { mutableStateOf(false) }
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val pokemonBuild = viewModel.getBuildForSpecies(speciesName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
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
                        text = "#$slotNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    AsyncImage(
                        model = SpriteUtils.getSpriteUrl(speciesName),
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
                                text = "Tier ${pokemonBuild.tier}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = when (pokemonBuild.tier) {
                                    "S" -> Color(0xFFFFD700) // Gold
                                    "A" -> Color(0xFF4CAF50) // Green
                                    "B" -> Color(0xFF2196F3) // Blue
                                    else -> Color.Gray
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

                    // Recommended build
                    if (pokemonBuild != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Recommended Build",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        if (pokemonBuild.notes != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pokemonBuild.notes,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        if (pokemonBuild.recommendedMoves.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Suggested moves:",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                pokemonBuild.recommendedMoves.forEach { moveName ->
                                    Text(
                                        text = "• $moveName",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        if (pokemonBuild.recommendedItem != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Item: ${pokemonBuild.recommendedItem}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
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
