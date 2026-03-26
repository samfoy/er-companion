package com.ercompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pokemon type badge with official type colors and 3-letter abbreviations.
 * Displays colored rounded rectangle badge with white text.
 *
 * @param typeId Pokemon type ID (0-17)
 * @param modifier Optional modifier
 * @param compact If true, uses smaller size for ultra-compact layouts
 */
@Composable
fun TypeBadge(typeId: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (_, color, abbreviation) = getTypeInfo(typeId)

    val fontSize = if (compact) 9.sp else 10.sp
    val horizontalPadding = if (compact) 3.dp else 4.dp
    val verticalPadding = if (compact) 1.dp else 2.dp
    val cornerRadius = if (compact) 2.dp else 3.dp

    Text(
        text = abbreviation,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(color, RoundedCornerShape(cornerRadius))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    )
}

/**
 * Returns type information: (name, color, 3-letter abbreviation)
 * Uses official Pokemon type colors for maximum recognizability
 */
private fun getTypeInfo(typeId: Int): Triple<String, Color, String> {
    return when (typeId) {
        0 -> Triple("Normal", Color(0xFFA8A878), "NRM")
        1 -> Triple("Fighting", Color(0xFFC03028), "FIG")
        2 -> Triple("Flying", Color(0xFFA890F0), "FLY")
        3 -> Triple("Poison", Color(0xFFA040A0), "POI")
        4 -> Triple("Ground", Color(0xFFE0C068), "GRD")
        5 -> Triple("Rock", Color(0xFFB8A038), "RCK")
        6 -> Triple("Bug", Color(0xFFA8B820), "BUG")
        7 -> Triple("Ghost", Color(0xFF705898), "GHO")
        8 -> Triple("Steel", Color(0xFFB8B8D0), "STL")
        9 -> Triple("Fire", Color(0xFFF08030), "FIR")
        10 -> Triple("Water", Color(0xFF6890F0), "WAT")
        11 -> Triple("Grass", Color(0xFF78C850), "GRS")
        12 -> Triple("Electric", Color(0xFFF8D030), "ELE")
        13 -> Triple("Psychic", Color(0xFFF85888), "PSY")
        14 -> Triple("Ice", Color(0xFF98D8D8), "ICE")
        15 -> Triple("Dragon", Color(0xFF7038F8), "DRG")
        16 -> Triple("Dark", Color(0xFF705848), "DRK")
        17 -> Triple("Fairy", Color(0xFFEE99AC), "FAI")
        else -> Triple("???", Color.Gray, "???")
    }
}
