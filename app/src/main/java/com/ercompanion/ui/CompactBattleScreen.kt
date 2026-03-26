package com.ercompanion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ercompanion.MainViewModel
import com.ercompanion.calc.ScoredMove
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

/**
 * Ultra-compact battle screen designed for small dual-screen handhelds (5.5" or less).
 * Focus: Maximum information density with minimal padding, icon-first design,
 * and gesture-based navigation.
 */
@Composable
fun CompactBattleScreen(
    viewModel: MainViewModel,
    connectionState: RetroArchClient.ConnectionStatus,
    dataSource: MainViewModel.DataSource,
    partyState: List<PartyMon?>,
    enemyPartyState: List<PartyMon?>,
    activePlayerSlot: Int,  // Legacy single slot for backwards compatibility
    scanningState: Boolean,
    errorMessage: String?,
    debugLog: List<String>,
    onRescan: () -> Unit
) {
    // Read active slots for doubles support (falls back to single slot)
    val activePlayerSlots = viewModel.activePlayerSlots.collectAsState().value
    val effectiveActiveSlots = activePlayerSlots.ifEmpty {
        if (activePlayerSlot >= 0) listOf(activePlayerSlot) else emptyList()
    }
    val activeEnemySlots = viewModel.activeEnemySlots.collectAsState().value
    val activeCurses = viewModel.curseState.collectAsState().value

    // State management
    var showOverlay by remember { mutableStateOf(false) }
    var showCurseDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(BattleTab.BOTH) }
    var expandedYourMonIndex by remember { mutableStateOf<Int?>(activePlayerSlot) }
    var expandedEnemyIndex by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }

    val enemyLead = enemyPartyState.firstOrNull()
    val inBattle = enemyLead != null
    val activeMon = effectiveActiveSlots.firstOrNull()?.let { partyState.getOrNull(it) }
                    ?: partyState.firstOrNull { it != null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    offsetY += dragAmount
                    if (offsetY > 100f) {
                        showOverlay = true
                        offsetY = 0f
                    } else if (offsetY < -100f) {
                        showOverlay = false
                        offsetY = 0f
                    }
                }
            }
    ) {
        if (partyState.isEmpty()) {
            // Empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (scanningState) "Scanning..." else "No battle",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else if (inBattle && enemyLead != null) {
            // Battle UI
            Column(modifier = Modifier.fillMaxSize()) {
                // Active curse indicator banner (at top)
                if (activeCurses.totalCurses() > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u26A0",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${activeCurses.totalCurses()} CURSES ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Minimal tab selector (only show if needed)
                if (selectedTab == BattleTab.TEAM || selectedTab == BattleTab.ENEMY) {
                    TabSelector(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // Main content area
                when (selectedTab) {
                    BattleTab.BOTH -> TwoColumnBattleLayout(
                        partyState = partyState,
                        enemyPartyState = enemyPartyState,
                        activePlayerSlots = effectiveActiveSlots,
                        activeEnemySlots = activeEnemySlots,
                        viewModel = viewModel,
                        expandedYourMonIndex = expandedYourMonIndex,
                        expandedEnemyIndex = expandedEnemyIndex,
                        onYourMonClick = { expandedYourMonIndex = if (expandedYourMonIndex == it) null else it },
                        onEnemyClick = { expandedEnemyIndex = it },
                        dataSource = dataSource
                    )
                    BattleTab.TEAM -> SingleColumnLayout(
                        mons = partyState,
                        activeSlots = effectiveActiveSlots,
                        enemyTarget = enemyPartyState.getOrNull(expandedEnemyIndex) ?: enemyLead,
                        viewModel = viewModel,
                        expandedIndex = expandedYourMonIndex,
                        onMonClick = { expandedYourMonIndex = if (expandedYourMonIndex == it) null else it },
                        isEnemy = false,
                        dataSource = dataSource
                    )
                    BattleTab.ENEMY -> SingleColumnLayout(
                        mons = enemyPartyState,
                        activeSlots = listOf(0),  // Enemy lead is always first slot
                        enemyTarget = activeMon,
                        viewModel = viewModel,
                        expandedIndex = expandedEnemyIndex,
                        onMonClick = { expandedEnemyIndex = it },
                        isEnemy = true,
                        dataSource = dataSource
                    )
                }
            }
        } else {
            // Out of battle - minimal single column
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(partyState) { index, mon ->
                    if (mon != null) {
                        MinimalMonChip(mon = mon, slotNumber = index + 1)
                    }
                }
            }
        }

        // Swipe-down overlay (connection status + debug)
        AnimatedVisibility(
            visible = showOverlay,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            StatusOverlay(
                connectionState = connectionState,
                dataSource = dataSource,
                errorMessage = errorMessage,
                debugLog = debugLog,
                viewModel = viewModel,
                onRescan = onRescan,
                onDismiss = { showOverlay = false }
            )
        }

        // Curse settings button (bottom-right corner)
        if (inBattle) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCurseDialog = true },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (activeCurses.totalCurses() > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\u26A0",
                            fontSize = 20.sp
                        )
                        if (activeCurses.totalCurses() > 0) {
                            Text(
                                text = activeCurses.totalCurses().toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Curse dialog
        if (showCurseDialog) {
            AlertDialog(
                onDismissRequest = { showCurseDialog = false },
                title = {
                    Text(
                        "Curse Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    CurseSelector(
                        curses = activeCurses,
                        onCursesChanged = { viewModel.updateCurses(it) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showCurseDialog = false }) {
                        Text("Close")
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f)
            )
        }
    }
}

enum class BattleTab { BOTH, TEAM, ENEMY }

@Composable
fun TabSelector(selectedTab: BattleTab, onTabSelected: (BattleTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(BattleTab.BOTH, BattleTab.TEAM, BattleTab.ENEMY).forEach { tab ->
            val label = when (tab) {
                BattleTab.BOTH -> "▦"
                BattleTab.TEAM -> "YOU"
                BattleTab.ENEMY -> "FOE"
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == tab) HPGreen else Color.Gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun TwoColumnBattleLayout(
    partyState: List<PartyMon?>,
    enemyPartyState: List<PartyMon?>,
    activePlayerSlots: List<Int>,
    activeEnemySlots: List<Int>,
    viewModel: MainViewModel,
    expandedYourMonIndex: Int?,
    expandedEnemyIndex: Int,
    onYourMonClick: (Int) -> Unit,
    onEnemyClick: (Int) -> Unit,
    dataSource: MainViewModel.DataSource
) {
    val activeMon = activePlayerSlots.firstOrNull()?.let { partyState.getOrNull(it) }
                    ?: partyState.firstOrNull { it != null }
    val selectedEnemy = enemyPartyState.getOrNull(expandedEnemyIndex) ?: enemyPartyState.firstOrNull()

    // Get all active enemies for doubles battle support
    val activeEnemies = activeEnemySlots.mapNotNull { enemyPartyState.getOrNull(it) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Your team column
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(partyState) { index, mon ->
                if (mon != null) {
                    val isActive = index in activePlayerSlots
                    val isExpanded = expandedYourMonIndex == index

                    // Show active Pokemon expanded by default, or show expanded if clicked
                    if (isExpanded || isActive) {
                        CompactMonCard(
                            mon = mon,
                            slotNumber = index + 1,
                            enemyTarget = selectedEnemy,
                            isActive = isActive,
                            viewModel = viewModel,
                            onClick = { onYourMonClick(index) },
                            isEnemy = false
                        )
                    } else {
                        UltraCompactChip(
                            mon = mon,
                            slotNumber = index + 1,
                            enemyTarget = selectedEnemy,
                            activeEnemies = activeEnemies,
                            isActive = isActive,
                            onClick = { onYourMonClick(index) }
                        )
                    }
                }
            }
        }

        // Enemy column
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(enemyPartyState) { index, enemy ->
                if (enemy != null) {
                    val isActive = index in activeEnemySlots
                    val isExpanded = expandedEnemyIndex == index

                    // Show active enemies expanded by default, or show expanded if clicked
                    if (isExpanded || isActive) {
                        CompactEnemyCard(
                            enemy = enemy,
                            activeMon = activeMon,
                            viewModel = viewModel,
                            onClick = { onEnemyClick(index) },
                            dataSource = dataSource
                        )
                    } else {
                        UltraCompactChip(
                            mon = enemy,
                            slotNumber = index + 1,
                            enemyTarget = activeMon,
                            activeEnemies = emptyList(), // Enemy mons don't need to see other enemies
                            isActive = isActive,
                            onClick = { onEnemyClick(index) },
                            isEnemy = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleColumnLayout(
    mons: List<PartyMon?>,
    activeSlots: List<Int>,
    enemyTarget: PartyMon?,
    viewModel: MainViewModel,
    expandedIndex: Int?,
    onMonClick: (Int) -> Unit,
    isEnemy: Boolean,
    dataSource: MainViewModel.DataSource
) {
    // Get all active enemies for damage calculations
    val activeEnemies = if (!isEnemy && enemyTarget != null) {
        listOf(enemyTarget).filterNotNull()
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(mons) { index, mon ->
            if (mon != null) {
                val isActive = index in activeSlots
                val isExpanded = expandedIndex == index

                if (isExpanded) {
                    if (isEnemy) {
                        CompactEnemyCard(
                            enemy = mon,
                            activeMon = enemyTarget,
                            viewModel = viewModel,
                            onClick = { onMonClick(index) },
                            dataSource = dataSource
                        )
                    } else {
                        CompactMonCard(
                            mon = mon,
                            slotNumber = index + 1,
                            enemyTarget = enemyTarget,
                            isActive = isActive,
                            viewModel = viewModel,
                            onClick = { onMonClick(index) },
                            isEnemy = false
                        )
                    }
                } else {
                    UltraCompactChip(
                        mon = mon,
                        slotNumber = index + 1,
                        enemyTarget = enemyTarget,
                        activeEnemies = activeEnemies,
                        isActive = isActive,
                        onClick = { onMonClick(index) },
                        isEnemy = isEnemy
                    )
                }
            }
        }
    }
}

/**
 * Ultra-compact chip for collapsed mons - absolute minimum size
 * Supports doubles battles by showing damage against multiple enemies
 */
@Composable
fun UltraCompactChip(
    mon: PartyMon,
    slotNumber: Int,
    enemyTarget: PartyMon?,
    activeEnemies: List<PartyMon>,
    isActive: Boolean,
    onClick: () -> Unit,
    isEnemy: Boolean = false
) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f

    // Calculate threat level for background color (from all active enemies in doubles)
    val bgColor = if (activeEnemies.isNotEmpty() && !isEnemy) {
        val totalIncomingDmg = calculateTotalIncomingDamage(mon, activeEnemies)
        when {
            totalIncomingDmg >= 100 -> Color(0xFF3A0A0A) // Red tint - OHKO danger
            totalIncomingDmg >= 50 -> Color(0xFF3A2A0A) // Orange tint - risky
            totalIncomingDmg <= 25 -> Color(0xFF0A2A0A) // Green tint - safe
            else -> Color(0xFF1A1A2A) // Default dark blue
        }
    } else if (enemyTarget != null && !isEnemy) {
        // Fallback to single enemy calculation
        val enemyDmgPct = calculateBestIncomingDamage(mon, enemyTarget)
        when {
            enemyDmgPct >= 100 -> Color(0xFF3A0A0A) // Red tint - OHKO danger
            enemyDmgPct >= 50 -> Color(0xFF3A2A0A) // Orange tint - risky
            enemyDmgPct <= 25 -> Color(0xFF0A2A0A) // Green tint - safe
            else -> Color(0xFF1A1A2A) // Default dark blue
        }
    } else if (isActive) {
        Color(0xFF0A2A0A) // Active green tint
    } else {
        Color(0xFF1A1A2A) // Default
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slot indicator (tiny)
            if (!isEnemy) {
                Text(
                    text = if (isActive) "▶" else "$slotNumber",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) HPGreen else Color.Gray,
                    modifier = Modifier.width(16.dp)
                )
            }

            // Sprite (small)
            val ctx = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(SpriteUtils.getSpriteUrl(speciesName))
                    .transformations(TopHalfCropTransformation())
                    .build(),
                contentDescription = speciesName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Name + HP bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = speciesName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnemy) Color(0xFFFF6B6B) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Level badge (tiny)
                    Text(
                        text = mon.level.toString(),
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }

                // HP bar with gradient and text inside
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.DarkGray)
                ) {
                    val hpColor = when {
                        hpFrac > 0.5f -> HPGreen
                        hpFrac > 0.2f -> HPYellow
                        else -> HPRed
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(hpColor)
                    )
                }
            }

            // Damage indicators (supports doubles battles)
            if (enemyTarget != null || activeEnemies.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Outgoing damage - show for each active enemy in doubles
                    if (activeEnemies.size > 1 && !isEnemy) {
                        val outgoingDmgs = calculateBestOutgoingDamageMulti(mon, activeEnemies)
                        if (outgoingDmgs.any { it > 0 }) {
                            val dmgText = outgoingDmgs.joinToString("/") { dmg ->
                                if (dmg >= 100) "KO" else "$dmg"
                            }
                            val maxDmg = outgoingDmgs.maxOrNull() ?: 0
                            val color = when {
                                maxDmg >= 100 -> HPRed
                                maxDmg >= 50 -> HPYellow
                                else -> Color(0xFF888888)
                            }
                            Text(
                                text = "→$dmgText",
                                fontSize = 9.sp,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (enemyTarget != null) {
                        // Single enemy
                        val outgoingPct = calculateBestOutgoingDamage(mon, enemyTarget)
                        if (outgoingPct > 0) {
                            val color = when {
                                outgoingPct >= 100 -> HPRed
                                outgoingPct >= 50 -> HPYellow
                                else -> Color(0xFF888888)
                            }
                            Text(
                                text = if (outgoingPct >= 100) "→KO" else "→$outgoingPct",
                                fontSize = 9.sp,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Incoming damage (only for player mons) - total from all active enemies
                    if (!isEnemy) {
                        val incomingPct = if (activeEnemies.isNotEmpty()) {
                            calculateTotalIncomingDamage(mon, activeEnemies)
                        } else if (enemyTarget != null) {
                            calculateBestIncomingDamage(mon, enemyTarget)
                        } else {
                            0
                        }

                        if (incomingPct <= 25) {
                            Text("SAFE", fontSize = 8.sp, color = HPGreen, fontWeight = FontWeight.Bold)
                        } else if (incomingPct > 0) {
                            val color = when {
                                incomingPct >= 100 -> HPRed
                                incomingPct >= 50 -> HPYellow
                                else -> Color(0xFF888888)
                            }
                            Text(
                                text = if (incomingPct >= 100) "←KO" else "←$incomingPct",
                                fontSize = 9.sp,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact expanded card showing moves and stats
 */
@Composable
fun CompactMonCard(
    mon: PartyMon,
    slotNumber: Int,
    enemyTarget: PartyMon?,
    isActive: Boolean,
    viewModel: MainViewModel,
    onClick: () -> Unit,
    isEnemy: Boolean
) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val types = PokemonData.getSpeciesTypes(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF0A2A0A) else Color(0xFF1A1A2A)
        ),
        shape = RoundedCornerShape(6.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, HPGreen) else null
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isEnemy) {
                        Text(
                            text = if (isActive) "▶" else "#$slotNumber",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) HPGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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

                    Column {
                        Text(
                            text = speciesName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnemy) Color(0xFFFF6B6B) else Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            types.forEach { typeId ->
                                TypeBadge(typeId, compact = true)
                            }
                        }
                    }
                }

                // Level badge
                Text(
                    text = mon.level.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // HP bar with text inside
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                // HP fill with gradient
                val hpColors = listOf(
                    when {
                        hpFrac > 0.5f -> HPGreen
                        hpFrac > 0.2f -> HPYellow
                        else -> HPRed
                    },
                    when {
                        hpFrac > 0.5f -> Color(0xFF66BB6A)
                        hpFrac > 0.2f -> Color(0xFFFFCA28)
                        else -> Color(0xFFE57373)
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(Brush.horizontalGradient(hpColors))
                )
                // HP text inside bar
                Text(
                    text = "${mon.hp}/${mon.maxHp}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.alpha(0.9f)
                )
            }

            // Speed comparison (if enemy target exists)
            if (enemyTarget != null && !isEnemy) {
                Spacer(modifier = Modifier.height(2.dp))
                val speedDiff = mon.speed - enemyTarget.speed
                val (label, color) = when {
                    speedDiff > 0 -> "SPD+" to HPGreen
                    speedDiff < 0 -> "SPD" to HPRed
                    else -> "SPD=" to Color(0xFFFF9800)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$label${if (speedDiff > 0) speedDiff else speedDiff}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            // Moves section
            if (mon.moves.isNotEmpty() && enemyTarget != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = Color(0xFF2A2A2A))
                Spacer(modifier = Modifier.height(4.dp))

                // Find best move
                val bestMoveId = mon.moves.filter { it != 0 }.maxByOrNull { moveId ->
                    val md = PokemonData.getMoveData(moveId)
                    if (md == null || md.power == 0) return@maxByOrNull 0
                    val attackStat = if (md.category == 0) mon.attack else mon.spAttack
                    val defenseStat = if (md.category == 0) enemyTarget.defense else enemyTarget.spDefense
                    val result = com.ercompanion.calc.DamageCalculator.calc(
                        mon.level, attackStat, defenseStat, md.power, md.type,
                        PokemonData.getSpeciesTypes(mon.species),
                        PokemonData.getSpeciesTypes(enemyTarget.species),
                        enemyTarget.maxHp
                    )
                    if (!result.isValid) 0 else result.maxDamage
                }

                mon.moves.forEachIndexed { idx, moveId ->
                    if (moveId != 0) {
                        CompactMoveRow(
                            mon = mon,
                            moveId = moveId,
                            moveIndex = idx,
                            enemyTarget = enemyTarget,
                            isBest = moveId == bestMoveId
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact enemy card with AI prediction
 */
@Composable
fun CompactEnemyCard(
    enemy: PartyMon,
    activeMon: PartyMon?,
    viewModel: MainViewModel,
    onClick: () -> Unit,
    dataSource: MainViewModel.DataSource
) {
    val speciesName = PokemonData.getSpeciesName(enemy.species)
    val types = PokemonData.getSpeciesTypes(enemy.species)
    val hpFrac = if (enemy.maxHp > 0) enemy.hp.toFloat() / enemy.maxHp else 0f

    var showAI by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column {
                        Text(
                            text = speciesName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            types.forEach { typeId ->
                                TypeBadge(typeId, compact = true)
                            }
                        }
                        // Ability/Item as tiny text indicators
                        if (enemy.ability > 0 || enemy.heldItem > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (enemy.ability > 0) {
                                    Text("ABL", fontSize = 7.sp, color = Color(0xFF9C27B0), fontWeight = FontWeight.Bold)
                                }
                                if (enemy.heldItem > 0) {
                                    Text("ITM", fontSize = 7.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = enemy.level.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFFFF4444), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // HP bar with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                val hpColors = listOf(
                    when {
                        hpFrac > 0.5f -> HPGreen
                        hpFrac > 0.2f -> HPYellow
                        else -> HPRed
                    },
                    when {
                        hpFrac > 0.5f -> Color(0xFF66BB6A)
                        hpFrac > 0.2f -> Color(0xFFFFCA28)
                        else -> Color(0xFFE57373)
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(Brush.horizontalGradient(hpColors))
                )
                Text(
                    text = "${enemy.hp}/${enemy.maxHp}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.alpha(0.9f)
                )
            }

            // AI Prediction toggle
            if (activeMon != null && enemy.moves.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAI = !showAI }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888)
                    )
                    Text(
                        text = if (showAI) "▲" else "▼",
                        fontSize = 10.sp,
                        color = Color(0xFF888888)
                    )
                }

                AnimatedVisibility(visible = showAI) {
                    Column {
                        Divider(color = Color(0xFF3A2A2A))
                        Spacer(modifier = Modifier.height(4.dp))

                        val scoredMoves = com.ercompanion.calc.BattleAISimulator.scoreMovesVsTarget(enemy, activeMon)
                        val predicted = com.ercompanion.calc.BattleAISimulator.predictAiMove(scoredMoves)

                        scoredMoves.sortedByDescending { it.score }.forEach { sm ->
                            val isPredicted = predicted.contains(sm)
                            CompactAIMoveRow(sm, activeMon, isPredicted)
                        }
                    }
                }
            }

            // Enemy moves
            if (enemy.moves.isNotEmpty() && activeMon != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = Color(0xFF3A2A2A))
                Spacer(modifier = Modifier.height(4.dp))

                enemy.moves.forEachIndexed { idx, moveId ->
                    if (moveId != 0) {
                        CompactMoveRow(
                            mon = enemy,
                            moveId = moveId,
                            moveIndex = idx,
                            enemyTarget = activeMon,
                            isBest = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMoveRow(
    mon: PartyMon,
    moveId: Int,
    moveIndex: Int,
    enemyTarget: PartyMon,
    isBest: Boolean
) {
    val moveData = PokemonData.getMoveData(moveId) ?: return
    val moveName = moveData.name
    val pp = mon.movePP.getOrNull(moveIndex) ?: 0

    // Status moves
    if (moveData.power == 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(moveData.type, compact = true)
                Spacer(modifier = Modifier.width(4.dp))
                Text(moveName, fontSize = 10.sp, color = Color.Gray)
            }
            if (pp > 0) {
                Text(
                    text = "$pp",
                    fontSize = 9.sp,
                    color = when {
                        pp <= 5 -> HPRed
                        pp <= 10 -> HPYellow
                        else -> Color.Gray
                    }
                )
            }
        }
        return
    }

    // Damage calc
    val attackStat = if (moveData.category == 0) mon.attack else mon.spAttack
    val defenseStat = if (moveData.category == 0) enemyTarget.defense else enemyTarget.spDefense
    val result = com.ercompanion.calc.DamageCalculator.calc(
        mon.level, attackStat, defenseStat, moveData.power, moveData.type,
        PokemonData.getSpeciesTypes(mon.species),
        PokemonData.getSpeciesTypes(enemyTarget.species),
        enemyTarget.maxHp
    )

    val effectColor = when {
        result.effectiveness == 0f -> Color.DarkGray
        result.effectiveness < 1f -> Color.Gray
        result.effectiveness > 1f -> Color(0xFFFF6B6B)
        else -> Color.White
    }

    val borderModifier = if (isBest) {
        Modifier.border(2.dp, HPGreen, RoundedCornerShape(3.dp))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .padding(horizontal = if (isBest) 3.dp else 0.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            TypeBadge(moveData.type, compact = true)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${moveData.power}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (pp > 0) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$pp",
                    fontSize = 9.sp,
                    color = when {
                        pp <= 5 -> HPRed
                        pp <= 10 -> HPYellow
                        else -> Color(0xFF666666)
                    }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.effectiveness > 1f) {
                Text("▲", fontSize = 9.sp, color = HPRed)
            } else if (result.effectiveness < 1f && result.effectiveness > 0f) {
                Text("▼", fontSize = 9.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "${result.percentMin}-${result.percentMax}%",
                fontSize = 10.sp,
                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                color = effectColor
            )
            if (result.wouldKO) {
                Text(" KO!", fontSize = 9.sp, color = HPRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CompactAIMoveRow(
    scoredMove: ScoredMove,
    target: PartyMon,
    isPredicted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isPredicted) "▶" else " ",
                fontSize = 10.sp,
                color = if (isPredicted) Color(0xFFFF6B6B) else Color.Transparent
            )
            Text(
                text = scoredMove.moveName,
                fontSize = 10.sp,
                fontWeight = if (isPredicted) FontWeight.Bold else FontWeight.Normal,
                color = if (isPredicted) Color.White else Color.Gray
            )
        }

        val dmgColor = when {
            scoredMove.damagePercent >= 100 -> HPRed
            scoredMove.damagePercent >= 50 -> HPYellow
            else -> Color(0xFF888888)
        }
        Text(
            text = if (scoredMove.damagePercent > 0) "${scoredMove.damagePercent}%" else scoredMove.label,
            fontSize = 10.sp,
            color = dmgColor
        )
    }
}


@Composable
fun MinimalMonChip(mon: PartyMon, slotNumber: Int) {
    val speciesName = PokemonData.getSpeciesName(mon.species)
    val hpFrac = if (mon.maxHp > 0) mon.hp.toFloat() / mon.maxHp else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$slotNumber",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.width(20.dp)
            )

            val ctx = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(SpriteUtils.getSpriteUrl(speciesName))
                    .transformations(TopHalfCropTransformation())
                    .build(),
                contentDescription = speciesName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = speciesName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.DarkGray)
                ) {
                    val hpColor = when {
                        hpFrac > 0.5f -> HPGreen
                        hpFrac > 0.2f -> HPYellow
                        else -> HPRed
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hpFrac.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(hpColor)
                    )
                }
            }

            Text(
                text = "${mon.level}",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun StatusOverlay(
    connectionState: RetroArchClient.ConnectionStatus,
    dataSource: MainViewModel.DataSource,
    errorMessage: String?,
    debugLog: List<String>,
    viewModel: MainViewModel,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD000000)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Connection indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
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
                Text(
                    text = when (connectionState) {
                        RetroArchClient.ConnectionStatus.CONNECTED -> "Connected"
                        RetroArchClient.ConnectionStatus.DISCONNECTED -> "Disconnected"
                        RetroArchClient.ConnectionStatus.ERROR -> "Error"
                    },
                    fontSize = 11.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onRescan,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Rescan", fontSize = 10.sp)
                }
            }

            // Data source
            if (connectionState == RetroArchClient.ConnectionStatus.CONNECTED) {
                Text(
                    text = when (dataSource) {
                        MainViewModel.DataSource.UDP -> "UDP LIVE"
                        MainViewModel.DataSource.SAVE_STATE -> "SAVE STATE"
                        MainViewModel.DataSource.DISCONNECTED -> ""
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (dataSource) {
                        MainViewModel.DataSource.UDP -> Color(0xFF4CAF50)
                        MainViewModel.DataSource.SAVE_STATE -> Color(0xFFFF9800)
                        MainViewModel.DataSource.DISCONNECTED -> Color.Gray
                    }
                )
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(errorMessage, fontSize = 10.sp, color = HPRed)
            }
        }
    }
}

// Helper functions for damage calculations
private fun calculateBestOutgoingDamage(attacker: PartyMon, defender: PartyMon): Int {
    return attacker.moves.filter { it != 0 }.mapNotNull { moveId ->
        val md = PokemonData.getMoveData(moveId) ?: return@mapNotNull null
        if (md.power == 0) return@mapNotNull null
        val result = com.ercompanion.calc.DamageCalculator.calc(
            attacker.level,
            if (md.category == 0) attacker.attack else attacker.spAttack,
            if (md.category == 0) defender.defense else defender.spDefense,
            md.power, md.type,
            PokemonData.getSpeciesTypes(attacker.species),
            PokemonData.getSpeciesTypes(defender.species),
            defender.maxHp
        )
        if (!result.isValid) return@mapNotNull null
        if (defender.maxHp > 0) (result.maxDamage * 100 / defender.maxHp) else 0
    }.maxOrNull() ?: 0
}

private fun calculateBestIncomingDamage(defender: PartyMon, attacker: PartyMon): Int {
    return attacker.moves.filter { it != 0 }.mapNotNull { moveId ->
        val md = PokemonData.getMoveData(moveId) ?: return@mapNotNull null
        if (md.power == 0) return@mapNotNull null
        val result = com.ercompanion.calc.DamageCalculator.calc(
            attacker.level,
            if (md.category == 0) attacker.attack else attacker.spAttack,
            if (md.category == 0) defender.defense else defender.spDefense,
            md.power, md.type,
            PokemonData.getSpeciesTypes(attacker.species),
            PokemonData.getSpeciesTypes(defender.species),
            defender.maxHp
        )
        if (!result.isValid) return@mapNotNull null
        if (defender.maxHp > 0) (result.maxDamage * 100 / defender.maxHp) else 0
    }.maxOrNull() ?: 0
}

// Doubles battle support: calculate damage against multiple enemies
private fun calculateBestOutgoingDamageMulti(attacker: PartyMon, defenders: List<PartyMon>): List<Int> {
    return defenders.map { defender -> calculateBestOutgoingDamage(attacker, defender) }
}

// Calculate total incoming threat from all active enemies
private fun calculateTotalIncomingDamage(defender: PartyMon, attackers: List<PartyMon>): Int {
    return attackers.sumOf { attacker -> calculateBestIncomingDamage(defender, attacker) }
}
