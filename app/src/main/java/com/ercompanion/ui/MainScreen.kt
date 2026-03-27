package com.ercompanion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
import com.ercompanion.calc.Weather
import com.ercompanion.data.MoveData
import com.ercompanion.data.PokemonData
import com.ercompanion.network.RetroArchClient
import com.ercompanion.parser.PartyMon
import com.ercompanion.ui.components.CurseSelector
import com.ercompanion.ui.components.TypeBadge
import com.ercompanion.ui.theme.HPGreen
import com.ercompanion.ui.theme.HPRed
import com.ercompanion.ui.theme.HPYellow
import com.ercompanion.utils.SpriteUtils
import com.ercompanion.utils.TopHalfCropTransformation

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    connectionState: RetroArchClient.ConnectionStatus,
    dataSource: MainViewModel.DataSource,
    partyState: List<PartyMon?>,
    enemyPartyState: List<PartyMon?>,
    activePlayerSlot: Int,
    scanningState: Boolean,
    errorMessage: String?,
    debugLog: List<String>,
    onRescan: () -> Unit
) {
    // Toggle between compact and classic layout
    // Set to true for ultra-compact mode (recommended for small screens)
    // Set to false for classic detailed mode
    val useCompactLayout = false

    // Use compact layout directly
    if (useCompactLayout) {
        CompactBattleScreen(
            viewModel = viewModel,
            connectionState = connectionState,
            dataSource = dataSource,
            partyState = partyState,
            enemyPartyState = enemyPartyState,
            activePlayerSlot = activePlayerSlot,
            scanningState = scanningState,
            errorMessage = errorMessage,
            debugLog = debugLog,
            onRescan = onRescan
        )
        return
    }

    // Classic layout below (original implementation)
    val scrollState = androidx.compose.foundation.rememberScrollState()
    var showDebug by remember { mutableStateOf(false) }
    var showCurseDialog by remember { mutableStateOf(false) }
    val activeCurses = viewModel.curseState.collectAsState().value

    val enemyLead = enemyPartyState.firstOrNull()
    val inBattle = enemyLead != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ── HEADER: compact single-row layout ──────────────────────
        if (inBattle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Title + debug tap
                Text(
                    text = "ER",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable { showDebug = !showDebug }
                )

                // Right: Status + Data Source + Rescan + Curses
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connection status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    RetroArchClient.ConnectionStatus.CONNECTED -> HPGreen
                                    RetroArchClient.ConnectionStatus.DISCONNECTED -> Color.Gray
                                    RetroArchClient.ConnectionStatus.ERROR -> HPRed
                                }
                            )
                    )

                    // Data source indicator
                    if (connectionState == RetroArchClient.ConnectionStatus.CONNECTED) {
                        Text(
                            text = when (dataSource) {
                                MainViewModel.DataSource.UDP -> "LIVE"
                                MainViewModel.DataSource.SAVE_STATE -> "SAVE"
                                MainViewModel.DataSource.DISCONNECTED -> ""
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (dataSource) {
                                MainViewModel.DataSource.UDP -> Color(0xFF4CAF50)
                                MainViewModel.DataSource.SAVE_STATE -> Color(0xFFFF9800)
                                MainViewModel.DataSource.DISCONNECTED -> Color.Gray
                            }
                        )
                    }

                    // Rescan button
                    TextButton(
                        onClick = onRescan,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("↺", fontSize = 14.sp, color = Color(0xFF888888))
                    }

                    // Curse button
                    TextButton(
                        onClick = { showCurseDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (activeCurses.totalCurses() > 0)
                                MaterialTheme.colorScheme.error
                            else
                                Color(0xFF888888)
                        )
                    ) {
                        Text(
                            text = if (activeCurses.totalCurses() > 0)
                                "⚠${activeCurses.totalCurses()}"
                            else
                                "⚠",
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Debug info (hidden by default, tap title to show)
            if (showDebug && errorMessage != null) {
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

            // Debug panel (only show in battle)
            DebugPanel(
                debugLog = debugLog,
                currentManualPath = viewModel.debugManualPath,
                saveStateStatus = viewModel.getSaveStateStatus(),
                searchPaths = viewModel.getSaveStateSearchPaths(),
                onApply = { path -> viewModel.applySaveStatePath(path) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Compact header for out-of-battle: just connection status and rescan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                RetroArchClient.ConnectionStatus.CONNECTED -> HPGreen
                                RetroArchClient.ConnectionStatus.DISCONNECTED -> Color.Gray
                                RetroArchClient.ConnectionStatus.ERROR -> HPRed
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = onRescan,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("↺", fontSize = 14.sp, color = Color(0xFF888888))
                }
                // Debug icon button (collapsed)
                TextButton(
                    onClick = { showDebug = !showDebug },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("🔧", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Debug panel (collapsed, only if user toggled it)
            if (showDebug) {
                DebugPanel(
                    debugLog = debugLog,
                    currentManualPath = viewModel.debugManualPath,
                    saveStateStatus = viewModel.getSaveStateStatus(),
                    searchPaths = viewModel.getSaveStateSearchPaths(),
                    onApply = { path -> viewModel.applySaveStatePath(path) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Track expanded mon in out-of-battle view
        var expandedOutOfBattleIndex by remember { mutableStateOf<Int?>(null) }

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
            // Read active slots for doubles support
            val activePlayerSlots = viewModel.activePlayerSlots.collectAsState().value
            val effectiveActiveSlots = activePlayerSlots.ifEmpty {
                if (activePlayerSlot >= 0) listOf(activePlayerSlot) else emptyList()
            }
            val activeEnemySlots = viewModel.activeEnemySlots.collectAsState().value
            val activeMon = effectiveActiveSlots.firstOrNull()?.let { partyState.getOrNull(it) }
                            ?: partyState.firstOrNull { it != null }

            // Track which bench mon is expanded (null = none)
            var expandedBenchIndex by remember { mutableStateOf<Int?>(null) }
            // Track which enemy mon is selected (default = lead)
            var selectedEnemyIndex by remember { mutableStateOf(0) }
            val selectedEnemy = enemyPartyState.getOrNull(selectedEnemyIndex) ?: enemyLead

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left column: our team
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
                            val isActive = index in effectiveActiveSlots
                            if (isActive) {
                                PokemonCard(
                                    viewModel = viewModel,
                                    mon = mon,
                                    slotNumber = index + 1,
                                    enemyTarget = selectedEnemy,
                                    isActive = true,
                                    showAiPrediction = false
                                )
                            } else {
                                val isExpanded = expandedBenchIndex == index
                                if (isExpanded) {
                                    PokemonCard(
                                        viewModel = viewModel,
                                        mon = mon,
                                        slotNumber = index + 1,
                                        enemyTarget = selectedEnemy,
                                        isActive = false,
                                        showAiPrediction = false,
                                        defaultExpanded = true,
                                        onHeaderClick = { expandedBenchIndex = null }
                                    )
                                } else {
                                    BenchedMonChip(
                                        mon = mon,
                                        enemyTarget = selectedEnemy,
                                        onClick = { expandedBenchIndex = index }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right column: enemy party
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ENEMY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAA4444),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    enemyPartyState.forEachIndexed { idx, enemy ->
                        if (enemy != null) {
                            val isActiveEnemy = idx in activeEnemySlots
                            if (idx == selectedEnemyIndex || isActiveEnemy) {
                                EnemyLeadCard(
                                    enemyLead = enemy,
                                    activeMon = activeMon,
                                    viewModel = viewModel,
                                    enemyPartyState = enemyPartyState,
                                    dataSource = dataSource
                                )
                            } else {
                                // Collapsed enemy chip
                                BenchedMonChip(
                                    mon = enemy,
                                    enemyTarget = activeMon,
                                    onClick = { selectedEnemyIndex = idx }
                                )
                            }
                        }
                    }
                }
            }

            // ── OPTIMAL LINE PREDICTION (below the two columns) ──────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            OptimalLineDisplay(
                activeMon = activeMon,
                enemyLead = enemyLead,
                viewModel = viewModel
            )
        } else {
            // ── OUT-OF-BATTLE LAYOUT: 2-column grid, all 6 mons visible ──────────────
            if (expandedOutOfBattleIndex != null) {
                // Show expanded view for single mon
                val mon = partyState.getOrNull(expandedOutOfBattleIndex!!)
                if (mon != null) {
                    PokemonCard(
                        viewModel = viewModel,
                        mon = mon,
                        slotNumber = expandedOutOfBattleIndex!! + 1,
                        enemyTarget = null,
                        isActive = false,
                        showAiPrediction = false,
                        defaultExpanded = true,
                        onHeaderClick = { expandedOutOfBattleIndex = null }
                    )
                }
            } else {
                // 2-column grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Left column: slots 0, 2, 4
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0, 2, 4).forEach { index ->
                            val mon = partyState.getOrNull(index)
                            if (mon != null) {
                                CompactPartyMonCard(
                                    mon = mon,
                                    slotNumber = index + 1,
                                    onClick = { expandedOutOfBattleIndex = index }
                                )
                            }
                        }
                    }
                    // Right column: slots 1, 3, 5
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1, 3, 5).forEach { index ->
                            val mon = partyState.getOrNull(index)
                            if (mon != null) {
                                CompactPartyMonCard(
                                    mon = mon,
                                    slotNumber = index + 1,
                                    onClick = { expandedOutOfBattleIndex = index }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Curse button is now in the header (removed floating action button)

        // Curse dialog
        if (showCurseDialog) {
            AlertDialog(
                onDismissRequest = { showCurseDialog = false },
                title = { Text("Curse Configuration") },
                text = {
                    CurseSelector(
                        curses = activeCurses,
                        onCursesChanged = { viewModel.updateCurses(it) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showCurseDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

/** Compact chip for benched mons during battle — shows sprite, name, HP bar, damage calc summary */
@Composable
fun BenchedMonChip(mon: PartyMon, enemyTarget: PartyMon?, onClick: (() -> Unit)? = null) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f
    val hpColor = when {
        hpFrac > 0.5f -> HPGreen
        hpFrac > 0.2f -> HPYellow
        else -> HPRed
    }

    // Best move damage this mon deals vs enemy
    val bestDmgPct = if (enemyTarget != null) {
        mon.moves.filter { it != 0 }.mapNotNull { moveId ->
            val md = PokemonData.getMoveData(moveId) ?: return@mapNotNull null
            if (md.power == 0) return@mapNotNull null
            val result = com.ercompanion.calc.DamageCalculator.calc(
                attackerLevel = mon.level,
                attackStat = if (md.category == 0) mon.attack else mon.spAttack,
                defenseStat = if (md.category == 0) enemyTarget.defense else enemyTarget.spDefense,
                movePower = md.power,
                moveType = md.type,
                attackerTypes = PokemonData.getSpeciesTypes(mon.species),
                defenderTypes = PokemonData.getSpeciesTypes(enemyTarget.species),
                targetMaxHP = enemyTarget.maxHp,
                moveCategory = md.category
            )
            if (!result.isValid) return@mapNotNull null
            if (enemyTarget.maxHp > 0) (result.maxDamage * 100 / enemyTarget.maxHp) else 0
        }.maxOrNull()
    } else null

    // Best move damage enemy deals vs this mon on switch-in
    val enemyDmgPct = if (enemyTarget != null) {
        enemyTarget.moves.filter { it != 0 }.mapNotNull { moveId ->
            val md = PokemonData.getMoveData(moveId) ?: return@mapNotNull null
            if (md.power == 0) return@mapNotNull null
            val result = com.ercompanion.calc.DamageCalculator.calc(
                attackerLevel = enemyTarget.level,
                attackStat = if (md.category == 0) enemyTarget.attack else enemyTarget.spAttack,
                defenseStat = if (md.category == 0) mon.defense else mon.spDefense,
                movePower = md.power,
                moveType = md.type,
                attackerTypes = PokemonData.getSpeciesTypes(enemyTarget.species),
                defenderTypes = PokemonData.getSpeciesTypes(mon.species),
                targetMaxHP = mon.maxHp,
                moveCategory = md.category
            )
            if (!result.isValid) return@mapNotNull null
            if (mon.maxHp > 0) (result.maxDamage * 100 / mon.maxHp) else 0
        }.maxOrNull()
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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
                    .data(SpriteUtils.getSpriteUrl(speciesName, mon.personality))
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Outgoing: best move vs enemy
                    if (bestDmgPct != null && bestDmgPct > 0) {
                        val color = when {
                            bestDmgPct >= 100 -> HPRed
                            bestDmgPct >= 50  -> HPYellow
                            else              -> Color.Gray
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Deals ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF666666), fontSize = 9.sp)
                            Text("${bestDmgPct}%", style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (bestDmgPct >= 100) Text(" KO!", fontSize = 8.sp, color = HPRed, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Incoming: enemy best move vs this mon
                    if (enemyDmgPct != null) {
                        if (enemyDmgPct <= 25) {
                            Text("SAFE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        } else {
                            val color = when {
                                enemyDmgPct >= 100 -> HPRed
                                enemyDmgPct >= 50  -> HPYellow
                                else              -> Color(0xFF888888)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Takes ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF666666), fontSize = 9.sp)
                                Text("${enemyDmgPct}%", style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                if (enemyDmgPct >= 100) Text(" KO!", fontSize = 8.sp, color = HPRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact card for out-of-battle party grid — shows sprite, name, level, types, HP bar, nature, ability */
@Composable
fun CompactPartyMonCard(mon: PartyMon, slotNumber: Int, onClick: () -> Unit) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val types = PokemonData.getSpeciesTypes(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f
    val hpColor = when {
        hpFrac > 0.5f -> HPGreen
        hpFrac > 0.2f -> HPYellow
        else -> HPRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Header: sprite + name + level
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$slotNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(SpriteUtils.getSpriteUrl(speciesName, mon.personality))
                            .transformations(TopHalfCropTransformation())
                            .build(),
                        contentDescription = speciesName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = speciesName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Lv${mon.level}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Type badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                types.forEach { typeId ->
                    TypeBadge(typeId)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // HP bar + numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${mon.hp}/${mon.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
                // Status condition
                if (mon.status > 0) {
                    val statusCondition = com.ercompanion.data.StatusData.getStatusCondition(mon.status)
                    if (statusCondition != null) {
                        Text(
                            text = statusCondition.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(statusCondition.color),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(hpColor)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Nature + Ability (one line each, small text)
            if (mon.personality > 0u) {
                val nature = com.ercompanion.data.NatureData.getNatureFromPersonality(mon.nature)
                if (!nature.isNeutral) {
                    Text(
                        text = "${nature.name} (+${nature.increasedStat} -${nature.decreasedStat})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
            if (mon.ability > 0) {
                val abilityName = com.ercompanion.data.AbilityData.getAbilityName(mon.ability)
                Text(
                    text = abilityName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9C27B0),
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EnemyLeadCard(enemyLead: PartyMon, activeMon: PartyMon?, viewModel: MainViewModel, enemyPartyState: List<PartyMon?>, dataSource: MainViewModel.DataSource) {
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
                        .data(SpriteUtils.getSpriteUrl(speciesName, enemyLead.personality))
                        .transformations(TopHalfCropTransformation())
                        .build(),
                    contentDescription = speciesName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = speciesName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    // Held item indicator
                    if (enemyLead.heldItem > 0) {
                        val itemEffect = com.ercompanion.data.ItemData.getItemEffect(enemyLead.heldItem)
                        if (itemEffect != null) {
                            Text(
                                text = "ITM: ${itemEffect.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD700),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    // Ability indicator
                    if (enemyLead.ability > 0) {
                        val abilityName = com.ercompanion.data.AbilityData.getAbilityName(enemyLead.ability)
                        val isHidden = com.ercompanion.data.SpeciesAbilities.isHiddenAbility(enemyLead.species, enemyLead.abilitySlot)
                        val abilityText = if (isHidden) {
                            "ABL: $abilityName (HA)"
                        } else {
                            "ABL: $abilityName"
                        }
                        Text(
                            text = abilityText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9C27B0),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // Nature indicator
                    if (enemyLead.personality > 0u) {
                        val nature = com.ercompanion.data.NatureData.getNatureFromPersonality(enemyLead.nature)
                        if (!nature.isNeutral) {
                            Text(
                                text = "${nature.name} (+${nature.increasedStat} -${nature.decreasedStat})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF9800),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        types.forEach { typeId ->
                            TypeBadge(typeId)
                        }
                    }
                    if (activeMon != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        SpeedComparisonIndicator(activeMon.speed, enemyLead.speed)
                    }
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
                    text = if (isRandom) "AI: random (${predicted.size} tied)" else "AI will likely use:",
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
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (sm.damagePercent > 0) "${sm.damagePercent}% vs $playerName" else sm.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = dmgColor
                                )
                            }
                        }
                        // OHKO/2HKO indicator for predicted moves
                        if (isPredicted && sm.damagePercent > 0) {
                            KOIndicator(sm.damagePercent, activeMon.hp, activeMon.maxHp)
                        }
                    }
                }
            }

            // Enemy moves with damage calculations
            if (enemyLead.moves.isNotEmpty() && activeMon != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF3A2A2A))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enemy Moves",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                enemyLead.moves.forEach { moveId ->
                    val moveData = com.ercompanion.data.PokemonData.getMoveData(moveId)
                    if (moveData != null) {
                        val damage = viewModel.calcDamage(enemyLead, activeMon, moveData, isEnemyAttacking = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = moveData.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                TypeBadge(moveData.type)
                            }

                            if (damage < 0) {
                                // Stats unavailable (pre-battle)
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                val damagePercent = (damage.toFloat() / activeMon.maxHp.toFloat() * 100).toInt()
                                val damageColor = when {
                                    damagePercent >= 75 -> Color(0xFFFF6B6B)  // Red - very dangerous
                                    damagePercent >= 50 -> Color(0xFFFF9800)  // Orange - dangerous
                                    damagePercent >= 25 -> Color(0xFFFFEB3B)  // Yellow - moderate
                                    else -> Color(0xFF4CAF50)  // Green - safe
                                }
                                Text(
                                    text = "${damage}dmg (${damagePercent}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = damageColor
                                )
                            }
                        }
                    }
                }
            }

            // Catch chance calculator (for wild Pokemon) - only show in save state mode
            if (dataSource == MainViewModel.DataSource.SAVE_STATE && enemyPartyState.size == 1 && activeMon != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF3A2A2A))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Catch Chance",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                val catchRate = com.ercompanion.calc.CatchRateCalculator.getSpeciesCatchRate(enemyLead.species)
                val balls = listOf(
                    "Poké Ball" to com.ercompanion.calc.CatchRateCalculator.POKE_BALL,
                    "Great Ball" to com.ercompanion.calc.CatchRateCalculator.GREAT_BALL,
                    "Ultra Ball" to com.ercompanion.calc.CatchRateCalculator.ULTRA_BALL,
                    "Quick Ball" to com.ercompanion.calc.CatchRateCalculator.QUICK_BALL,
                    "Timer Ball" to com.ercompanion.calc.CatchRateCalculator.TIMER_BALL,
                    "Dusk Ball" to com.ercompanion.calc.CatchRateCalculator.DUSK_BALL,
                    "Repeat Ball" to com.ercompanion.calc.CatchRateCalculator.REPEAT_BALL,
                    "Net Ball" to com.ercompanion.calc.CatchRateCalculator.NET_BALL,
                    "Dive Ball" to com.ercompanion.calc.CatchRateCalculator.DIVE_BALL
                )

                balls.forEach { (ballName, ballMod) ->
                    val chance = com.ercompanion.calc.CatchRateCalculator.calculateCatchChance(
                        enemyLead.hp, enemyLead.maxHp, catchRate, ballMod
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ballName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            // Shake indicators
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "o".repeat(chance.shakeCount),
                                fontSize = 10.sp,
                                color = Color(0xFF888888)
                            )
                        }
                        val percentColor = when {
                            chance.percentChance >= 75 -> Color(0xFF4CAF50)
                            chance.percentChance >= 50 -> Color(0xFFFFEB3B)
                            chance.percentChance >= 25 -> Color(0xFFFF9800)
                            else -> Color(0xFFFF6B6B)
                        }
                        Text(
                            text = "${chance.percentChance}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = percentColor
                        )
                    }
                }

                // Tip for status effects
                Text(
                    text = "TIP: Sleep/Freeze: 2x catch rate",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PokemonCard(viewModel: MainViewModel, mon: PartyMon, slotNumber: Int, enemyTarget: PartyMon? = null, isActive: Boolean = false, showAiPrediction: Boolean = false, defaultExpanded: Boolean = false, onHeaderClick: (() -> Unit)? = null) {
    // Key on isActive so the card auto-expands when this mon becomes the active battler
    var expanded by remember(mon.species, isActive) { mutableStateOf(defaultExpanded || isActive) }
    var buildsExpanded by remember(mon.species) { mutableStateOf(false) }
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val pokemonBuild = viewModel.getBuildForSpecies(speciesName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onHeaderClick != null && expanded) onHeaderClick()
                else expanded = !expanded
            },
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
                            .data(SpriteUtils.getSpriteUrl(speciesName, mon.personality))
                            .transformations(TopHalfCropTransformation())
                            .build(),
                        contentDescription = speciesName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 8.dp)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = speciesName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pokemonBuild?.tier != null) {
                                Spacer(modifier = Modifier.width(6.dp))
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
                        // Held item indicator
                        if (mon.heldItem > 0) {
                            val itemName = com.ercompanion.data.ItemData.getItemName(mon.heldItem)
                            Text(
                                text = "ITM: $itemName",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD700),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        // Ability indicator
                        if (mon.ability > 0) {
                            val abilityName = com.ercompanion.data.AbilityData.getAbilityName(mon.ability)
                            val isHidden = com.ercompanion.data.SpeciesAbilities.isHiddenAbility(mon.species, mon.abilitySlot)
                            val abilityText = if (isHidden) {
                                "ABL: $abilityName (HA)"
                            } else {
                                "ABL: $abilityName"
                            }
                            Text(
                                text = abilityText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9C27B0),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        // Nature indicator
                        if (mon.personality > 0u) {
                            val nature = com.ercompanion.data.NatureData.getNatureFromPersonality(mon.nature)
                            if (!nature.isNeutral) {
                                Text(
                                    text = nature.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF9800),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        // Type badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val types = PokemonData.getSpeciesTypes(mon.species)
                            types.forEach { typeId ->
                                TypeBadge(typeId)
                            }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${mon.hp} / ${mon.maxHp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Status condition
                        if (mon.status > 0) {
                            val statusCondition = com.ercompanion.data.StatusData.getStatusCondition(mon.status)
                            if (statusCondition != null) {
                                Text(
                                    text = statusCondition.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(statusCondition.color),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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

                    // Nature display
                    if (mon.personality > 0u) {
                        val nature = com.ercompanion.data.NatureData.getNatureFromPersonality(mon.nature)
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${nature.name} Nature",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )
                            if (!nature.isNeutral) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "+${nature.increasedStat}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "-${nature.decreasedStat}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF44336),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Neutral",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Held item effects
                    if (mon.heldItem > 0) {
                        val itemEffect = com.ercompanion.data.ItemData.getItemEffect(mon.heldItem)
                        if (itemEffect != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                            ) {
                                Text(
                                    text = "ITM: ${itemEffect.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold
                                )
                                if (itemEffect.description.isNotEmpty()) {
                                    Text(
                                        text = itemEffect.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCCCCCC),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                // Show modified stats if item affects them
                                val modifiedStats = com.ercompanion.data.ItemData.applyItemToStats(
                                    attack = mon.attack,
                                    defense = mon.defense,
                                    spAttack = mon.spAttack,
                                    spDefense = mon.spDefense,
                                    speed = mon.speed,
                                    itemId = mon.heldItem,
                                    currentHp = mon.hp,
                                    maxHp = mon.maxHp
                                )

                                val hasStatChange = modifiedStats.attack != mon.attack ||
                                    modifiedStats.defense != mon.defense ||
                                    modifiedStats.spAttack != mon.spAttack ||
                                    modifiedStats.spDefense != mon.spDefense ||
                                    modifiedStats.speed != mon.speed

                                if (hasStatChange) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Modified Stats:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                        fontSize = 9.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (modifiedStats.attack != mon.attack) {
                                            Text("ATK: ${modifiedStats.attack}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                        if (modifiedStats.defense != mon.defense) {
                                            Text("DEF: ${modifiedStats.defense}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                        if (modifiedStats.speed != mon.speed) {
                                            Text("SPD: ${modifiedStats.speed}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (modifiedStats.spAttack != mon.spAttack) {
                                            Text("SP.ATK: ${modifiedStats.spAttack}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                        if (modifiedStats.spDefense != mon.spDefense) {
                                            Text("SP.DEF: ${modifiedStats.spDefense}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                }
                            }
                        }
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

                    // Hidden Power calculator
                    if (mon.ivHp > 0 || mon.ivAttack > 0) {  // Check if we have IV data
                        val hiddenPower = com.ercompanion.calc.HiddenPowerCalculator.calculate(
                            ivHp = mon.ivHp,
                            ivAttack = mon.ivAttack,
                            ivDefense = mon.ivDefense,
                            ivSpeed = mon.ivSpeed,
                            ivSpAttack = mon.ivSpAttack,
                            ivSpDefense = mon.ivSpDefense
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hidden Power",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFFEB3B),
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TypeBadge(
                                    typeId = when (hiddenPower.typeName) {
                                        "Normal" -> 0
                                        "Fighting" -> 1
                                        "Flying" -> 2
                                        "Poison" -> 3
                                        "Ground" -> 4
                                        "Rock" -> 5
                                        "Bug" -> 6
                                        "Ghost" -> 7
                                        "Steel" -> 8
                                        "Fire" -> 9
                                        "Water" -> 10
                                        "Grass" -> 11
                                        "Electric" -> 12
                                        "Psychic" -> 13
                                        "Ice" -> 14
                                        "Dragon" -> 15
                                        "Dark" -> 16
                                        "Fairy" -> 17
                                        else -> 0
                                    }
                                )
                                Text(
                                    text = "Pwr: ${hiddenPower.power}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
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

                        // Calculate best move for highlighting
                        val bestMoveId = if (enemyTarget != null) {
                            mon.moves.filter { it != 0 }.maxByOrNull { moveId ->
                                val md = PokemonData.getMoveData(moveId)
                                if (md == null || md.power == 0) return@maxByOrNull 0
                                val attackStat = if (md.category == 0) mon.attack else mon.spAttack
                                val defenseStat = if (md.category == 0) enemyTarget.defense else enemyTarget.spDefense
                                val result = com.ercompanion.calc.DamageCalculator.calc(
                                    attackerLevel = mon.level,
                                    attackStat = attackStat,
                                    defenseStat = defenseStat,
                                    movePower = md.power,
                                    moveType = md.type,
                                    attackerTypes = PokemonData.getSpeciesTypes(mon.species),
                                    defenderTypes = PokemonData.getSpeciesTypes(enemyTarget.species),
                                    targetMaxHP = enemyTarget.maxHp,
                                    moveCategory = md.category
                                )
                                if (!result.isValid) return@maxByOrNull 0
                                result.maxDamage
                            }
                        } else null

                        mon.moves.forEachIndexed { moveIndex, moveId ->
                            MoveItem(
                                mon = mon,
                                moveId = moveId,
                                moveIndex = moveIndex,
                                enemyTarget = enemyTarget,
                                isBestMove = moveId == bestMoveId,
                                isRecommended = pokemonBuild?.recommendedMoves?.contains(PokemonData.getMoveName(moveId)) == true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoveItem(mon: PartyMon, moveId: Int, moveIndex: Int, enemyTarget: PartyMon?, isBestMove: Boolean = false, isRecommended: Boolean = false) {
    val moveName = PokemonData.getMoveName(moveId)
    val moveData = PokemonData.getMoveData(moveId)
    val pp = mon.movePP.getOrNull(moveIndex) ?: 0

    if (moveData == null || moveData.power == 0) {
        // Status move or unknown move
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "• $moveName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REC", fontSize = 8.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            if (pp > 0) {
                Text(
                    text = "PP: $pp",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        pp <= 5 -> Color(0xFFF44336)
                        pp <= 10 -> Color(0xFFFFEB3B)
                        else -> Color.Gray
                    },
                    fontSize = 9.sp
                )
            }
        }
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
        moveCategory = moveData.category,  // Required: 0=Physical, 1=Special
        attackerTypes = attackerTypes,
        defenderTypes = defenderTypes,
        targetMaxHP = enemyTarget.maxHp,
        isBurned = false,
        weather = Weather.NONE,
        moveName = moveName
    )

    // Check if result is invalid (pre-battle)
    if (!result.isValid) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "• $moveName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REC", fontSize = 8.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
        return
    }

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = if (isBestMove) Color(0xFF1A3A1A) else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isBestMove) 8.dp else 0.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBestMove) "⭐ $moveName" else "• $moveName",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBestMove) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isBestMove) FontWeight.Bold else FontWeight.Normal
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REC", fontSize = 8.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = damageText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = effectColor,
                        fontWeight = if (result.effectiveness > 1f || isBestMove) FontWeight.Bold else FontWeight.Normal
                    )
                    if (result.wouldKO) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "KO!", fontSize = 10.sp, color = HPRed, fontWeight = FontWeight.Bold)
                    }
                }
                // PP display
                if (pp > 0) {
                    Text(
                        text = "PP: $pp",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            pp <= 5 -> Color(0xFFF44336)
                            pp <= 10 -> Color(0xFFFFEB3B)
                            else -> Color(0xFF888888)
                        },
                        fontSize = 8.sp
                    )
                }
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

// ══════════════════════════════════════════════════════════════════════════════
// Helper Composables for Enhanced Features
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun SpeedComparisonIndicator(playerSpeed: Int, enemySpeed: Int) {
    val (text, color) = when {
        playerSpeed > enemySpeed -> Pair("SPD +${playerSpeed - enemySpeed}", Color(0xFF4CAF50))
        playerSpeed < enemySpeed -> Pair("SPD -${enemySpeed - playerSpeed}", Color(0xFFFF6B6B))
        else -> Pair("SPD Tie", Color(0xFFFF9800))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun StatStageIndicator(stages: List<Int>) {
    // Stages: [ATK, DEF, SPD, SPA, SPD, ACC, EVA, ...]
    // Baseline is 6, so stages[i] - 6 = actual modifier
    val statNames = listOf("ATK", "DEF", "SPD", "SPA", "SPD")
    val relevantStages = stages.take(5)

    val modifiedStats = relevantStages.mapIndexed { idx, stage ->
        val modifier = stage - 6
        if (modifier != 0) statNames[idx] to modifier else null
    }.filterNotNull()

    if (modifiedStats.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            modifiedStats.forEach { (stat, mod) ->
                val color = if (mod > 0) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
                val sign = if (mod > 0) "+" else ""
                Surface(
                    color = color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "$stat$sign$mod",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KOIndicator(damagePercent: Int, currentHp: Int, maxHp: Int) {
    val hpPercent = if (maxHp > 0) (currentHp * 100 / maxHp) else 0

    when {
        damagePercent >= hpPercent -> {
            // OHKO
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OHKO!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF0000)
                )
            }
        }
        damagePercent * 2 >= hpPercent -> {
            // 2HKO
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "2HKO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
fun OptimalLineDisplay(
    activeMon: PartyMon?,
    enemyLead: PartyMon?,
    viewModel: MainViewModel
) {
    if (activeMon == null || enemyLead == null) return

    var expanded by remember { mutableStateOf(false) }
    // 0=2-turn, 1=3-turn, 2=MC (Deep/50 samples), 3=MC+ (Exhaustive/100 samples + switching)
    var analysisMode by remember { mutableStateOf(0) }
    // Reset mcRunning when Pokemon change to prevent stale simulations
    var mcRunning by remember(activeMon.species, enemyLead.species) { mutableStateOf(false) }
    val activeCurses = viewModel.curseState.collectAsState().value
    val coroutineScope = rememberCoroutineScope()

    // Standard lines (modes 0 and 1) — cached
    val lines = remember(activeMon.species, enemyLead.species, activeMon.moves, enemyLead.moves, activeCurses, analysisMode) {
        if (analysisMode >= 2) return@remember emptyList()
        try {
            com.ercompanion.calc.OptimalLineCalculator.calculateOptimalLines(
                player = activeMon,
                enemy = enemyLead,
                maxDepth = if (analysisMode == 1) 3 else 2,
                topN = if (analysisMode == 1) 5 else 3,
                isTrainer = true,
                curses = activeCurses
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Monte Carlo report (modes 2/3) — runs async, separate cache per depth
    // IMPORTANT: Key on species/moves/curses so reports reset when Pokemon change
    var mcReport by remember(activeMon.species, enemyLead.species, activeMon.moves, enemyLead.moves, activeCurses) { mutableStateOf<com.ercompanion.calc.DeepAnalysisReport?>(null) }
    var mcReportPlus by remember(activeMon.species, enemyLead.species, activeMon.moves, enemyLead.moves, activeCurses) { mutableStateOf<com.ercompanion.calc.DeepAnalysisReport?>(null) }
    var mcProgress by remember(activeMon.species, enemyLead.species) { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚔", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        text = "OPTIMAL LINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mode toggle: 2T / 3T / MC / MC+
                    listOf("2T", "3T", "MC", "MC+").forEachIndexed { idx, label ->
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = if (analysisMode == idx) FontWeight.Bold else FontWeight.Normal,
                            color = if (analysisMode == idx) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier
                                .clickable {
                                    analysisMode = idx
                                    if (!expanded) expanded = true
                                    val isMcMode = idx == 2 || idx == 3
                                    val alreadyHasReport = if (idx == 2) mcReport != null else mcReportPlus != null
                                    if (isMcMode && !alreadyHasReport && !mcRunning) {
                                        mcRunning = true
                                        mcProgress = if (idx == 3) "Running full analysis…" else "Analyzing…"
                                        coroutineScope.launch(Dispatchers.Default) {
                                            try {
                                                val depth = if (idx == 3)
                                                    com.ercompanion.calc.AnalysisDepth.EXHAUSTIVE
                                                else
                                                    com.ercompanion.calc.AnalysisDepth.DEEP
                                                val result = com.ercompanion.calc.DeepAnalysisMode.performDeepAnalysis(
                                                    player = activeMon,
                                                    enemy = enemyLead,
                                                    depth = depth,
                                                    curses = activeCurses,
                                                    onProgress = { msg -> mcProgress = msg }
                                                )
                                                if (idx == 3) mcReportPlus = result else mcReport = result
                                            } catch (e: Exception) {
                                                mcProgress = "Error: ${e.message}"
                                            } finally {
                                                mcRunning = false
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        if (idx < 3) Text(text = "/", fontSize = 9.sp, color = Color(0xFF555555), modifier = Modifier.padding(horizontal = 1.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

                        // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Divider(color = Color(0xFF3A2A3A), modifier = Modifier.padding(bottom = 8.dp))

                    if (analysisMode >= 2) {
                        // Monte Carlo mode
                        when {
                            mcRunning -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFFFD700)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = mcProgress, fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            (if (analysisMode == 3) mcReportPlus else mcReport) != null -> {
                                val report = (if (analysisMode == 3) mcReportPlus else mcReport)!!
                                // Win rate
                                report.monteCarloResult?.let { mc ->
                                    val winPct = (mc.winRate * 100).toInt()
                                    val lossRate = ((1f - mc.winRate) * 100).toInt()
                                    val winColor = when {
                                        winPct >= 70 -> Color(0xFF4CAF50)
                                        winPct >= 40 -> Color(0xFFFFEB3B)
                                        else -> Color(0xFFF44336)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Win rate", fontSize = 10.sp, color = Color.Gray)
                                        Text(
                                            text = "$winPct% win  /  $lossRate% loss",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = winColor
                                        )
                                    }
                                    Text(
                                        text = "Avg turns to KO: ${String.format("%.1f", (mc.avgTurnsToWin.toFloat()))}",
                                        fontSize = 10.sp, color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Divider(color = Color(0xFF3A2A3A), modifier = Modifier.padding(vertical = 6.dp))
                                }
                                // Top lines from deep analysis
                                report.optimalLines.take(5).forEachIndexed { index, line ->
                                    OptimalLineItem(line = line, rank = index + 1, activeMon = activeMon)
                                    if (index < minOf(4, report.optimalLines.size - 1)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                                // Switch recommendations if any
                                report.switchRecommendations?.takeIf { it.isNotEmpty() }?.let { switches ->
                                    Divider(color = Color(0xFF3A2A3A), modifier = Modifier.padding(vertical = 6.dp))
                                    Text(text = "⇄ Switch suggestions:", fontSize = 10.sp, color = Color(0xFF88AAFF), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    switches.take(2).forEach { sw ->
                                        val swName = com.ercompanion.data.PokemonData.getSpeciesName(sw.targetMon.species)
                                        Text(
                                            text = "→ $swName (score: ${String.format("%.1f", sw.score)})",
                                            fontSize = 10.sp, color = Color.Gray
                                        )
                                    }
                                }
                                Text(
                                    text = "Analysis took ${report.analysisTimeMs}ms",
                                    fontSize = 8.sp, color = Color(0xFF444444),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            else -> {
                                Text(text = "Tap MC to run analysis", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        // Standard 2T/3T lines
                        if (lines.isEmpty()) {
                            Text(
                                text = "No optimal lines calculated",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        } else {
                            lines.forEachIndexed { index, line ->
                                OptimalLineItem(line = line, rank = index + 1, activeMon = activeMon)
                                if (index < lines.size - 1) Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptimalLineItem(line: com.ercompanion.calc.BattleLine, rank: Int, activeMon: PartyMon) {
    // Color code by score
    val scoreColor = when {
        line.score >= 8f -> Color(0xFF4CAF50)  // Green
        line.score >= 6f -> Color(0xFFFFEB3B)  // Yellow
        else -> Color(0xFFFF9800)              // Orange/Red
    }

    // Risk indicator
    val isRisky = line.survivalProbability < 0.4f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (rank == 1) Color(0xFF2A1A2A) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // Move sequence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rank == 1) "▶" else " $rank",
                    fontSize = 12.sp,
                    fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (rank == 1) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    // Move names
                    val moveNames = line.moves.map { moveId ->
                        PokemonData.getMoveName(moveId)
                    }
                    Text(
                        text = moveNames.joinToString(" → "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (rank == 1) Color.White else Color(0xFFCCCCCC),
                        fontSize = 11.sp
                    )
                }
            }

            // Score
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRisky) {
                    Text(
                        text = "⚠",
                        fontSize = 11.sp,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = "%.1f".format(line.score),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    fontSize = 12.sp
                )
                Text(
                    text = "/10",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        // Details: turns to KO, survival
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Turns to KO
            val koText = when {
                line.turnsToKO == 1 -> "✓ OHKO"
                line.turnsToKO == 2 -> "✓ 2HKO"
                line.turnsToKO == 3 -> "✓ 3HKO"
                line.turnsToKO > 0 -> "✓ ${line.turnsToKO}HKO"
                else -> "No KO"
            }
            val koColor = when {
                line.turnsToKO == 1 -> Color(0xFF4CAF50)
                line.turnsToKO == 2 -> Color(0xFFFFEB3B)
                line.turnsToKO > 0 -> Color(0xFFFF9800)
                else -> Color.Gray
            }

            Text(
                text = koText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = koColor,
                fontWeight = if (line.turnsToKO <= 2) FontWeight.Bold else FontWeight.Normal
            )

            // Survival probability
            val survivalPercent = (line.survivalProbability * 100).toInt()
            val survivalColor = when {
                survivalPercent >= 70 -> Color(0xFF4CAF50)
                survivalPercent >= 40 -> Color(0xFFFFEB3B)
                else -> Color(0xFFFF6B6B)
            }

            Text(
                text = "${survivalPercent}% surv",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = survivalColor,
                fontWeight = if (isRisky) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
