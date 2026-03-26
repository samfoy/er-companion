package com.ercompanion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ercompanion.calc.CurseState

/**
 * Curse selector UI component.
 * Allows toggling individual curses on/off.
 */
@Composable
fun CurseSelector(
    curses: CurseState,
    onCursesChanged: (CurseState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Active Curses",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "Curses make enemies stronger but increase rewards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider()

        // Stackable curses with sliders
        CurseSlider(
            label = "Crit Curse",
            description = "Enemy crit chance +10% per curse",
            value = curses.critCurse,
            maxValue = 9,
            onValueChange = { onCursesChanged(curses.copy(critCurse = it)) }
        )

        CurseSlider(
            label = "Adaptability Curse",
            description = "Enemy STAB damage +5% per curse",
            value = curses.adaptabilityCurse,
            maxValue = 3,
            onValueChange = { onCursesChanged(curses.copy(adaptabilityCurse = it)) }
        )

        CurseSlider(
            label = "Endure Curse",
            description = "Enemy survives with 1 HP, +20% per curse",
            value = curses.endureCurse,
            maxValue = 4,
            onValueChange = { onCursesChanged(curses.copy(endureCurse = it)) }
        )

        CurseSlider(
            label = "Priority Curse",
            description = "Enemy moves gain +1 priority, +10% per curse",
            value = curses.priorityCurse,
            maxValue = 9,
            onValueChange = { onCursesChanged(curses.copy(priorityCurse = it)) }
        )

        CurseSlider(
            label = "Serene Grace Curse",
            description = "Enemy secondary effects +50% per curse",
            value = curses.sereneGraceCurse,
            maxValue = 3,
            onValueChange = { onCursesChanged(curses.copy(sereneGraceCurse = it)) }
        )

        CurseSlider(
            label = "Flinch Curse",
            description = "Enemy flinch chance +10% per curse",
            value = curses.flinchCurse,
            maxValue = 9,
            onValueChange = { onCursesChanged(curses.copy(flinchCurse = it)) }
        )

        CurseSlider(
            label = "Shed Skin Curse",
            description = "Enemy status cure chance +15% per curse",
            value = curses.shedSkinCurse,
            maxValue = 6,
            onValueChange = { onCursesChanged(curses.copy(shedSkinCurse = it)) }
        )

        Divider()

        // Binary curses with switches
        CurseSwitch(
            label = "⚠️ OHKO Curse",
            description = "Enemy attacks ALWAYS OHKO - Extreme!",
            checked = curses.ohkoCurse,
            onCheckedChange = { onCursesChanged(curses.copy(ohkoCurse = it)) },
            isWarning = true
        )

        CurseSwitch(
            label = "Unaware Curse",
            description = "Enemy ignores your stat stages",
            checked = curses.unawareCurse,
            onCheckedChange = { onCursesChanged(curses.copy(unawareCurse = it)) }
        )

        CurseSwitch(
            label = "Torment Curse",
            description = "You can't use the same move twice",
            checked = curses.tormentCurse,
            onCheckedChange = { onCursesChanged(curses.copy(tormentCurse = it)) }
        )

        CurseSwitch(
            label = "Pressure Curse",
            description = "Your moves cost +1 PP",
            checked = curses.pressureCurse,
            onCheckedChange = { onCursesChanged(curses.copy(pressureCurse = it)) }
        )
    }
}

@Composable
private fun CurseSlider(
    label: String,
    description: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (value == 0) "OFF" else "×$value",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..maxValue.toFloat(),
            steps = maxValue - 1,
            enabled = maxValue > 0
        )
    }
}

@Composable
private fun CurseSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isWarning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isWarning && checked) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
