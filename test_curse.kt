import com.ercompanion.calc.DamageCalculator
import com.ercompanion.calc.CurseState

fun main() {
    val noCurse = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 9,  // Fire
        attackerTypes = listOf(9),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        isEnemyAttacking = true,
        curses = CurseState.NONE
    )

    val threeCurses = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 9,  // Fire
        attackerTypes = listOf(9),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        isEnemyAttacking = true,
        curses = CurseState(adaptabilityCurse = 3)
    )

    println("No curse damage: ${noCurse.maxDamage}")
    println("3 curses damage: ${threeCurses.maxDamage}")
    println("Ratio: ${threeCurses.maxDamage.toFloat() / noCurse.maxDamage.toFloat()}")
}
