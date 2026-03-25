package com.ercompanion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ercompanion.data.PokemonData
import com.ercompanion.network.RetroArchClient
import com.ercompanion.parser.PartyMon
import com.ercompanion.ui.theme.HPGreen
import com.ercompanion.ui.theme.HPRed
import com.ercompanion.ui.theme.HPYellow

@Composable
fun MainScreen(
    connectionState: RetroArchClient.ConnectionStatus,
    partyState: List<PartyMon?>,
    scanningState: Boolean,
    errorMessage: String?,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            Spacer(modifier = Modifier.height(16.dp))
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(partyState) { index, mon ->
                    if (mon != null) {
                        PokemonCard(mon = mon, slotNumber = index + 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PokemonCard(mon: PartyMon, slotNumber: Int) {
    var expanded by remember { mutableStateOf(false) }

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
            // Header: Slot number, name, level
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
                    Text(
                        text = PokemonData.getSpeciesName(mon.species),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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

                    // Moves
                    if (mon.moves.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Moves",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        mon.moves.forEach { moveId ->
                            Text(
                                text = "• ${PokemonData.getMoveName(moveId)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
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
