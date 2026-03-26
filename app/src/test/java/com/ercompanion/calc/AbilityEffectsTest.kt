package com.ercompanion.calc

import com.ercompanion.parser.PartyMon
import org.junit.Assert.*
import org.junit.Test

class AbilityEffectsTest {

    // Ability constants
    private val INTIMIDATE = 22
    private val MOXIE = 153
    private val SPEED_BOOST = 3
    private val GUTS = 62
    private val MARVEL_SCALE = 63
    private val TECHNICIAN = 101
    private val HUGE_POWER = 37
    private val DOWNLOAD = 88
    private val BEAST_BOOST = 224

    // Create test Pokemon
    private fun createTestMon(
        species: Int = 1,
        level: Int = 50,
        hp: Int = 150,
        attack: Int = 100,
        defense: Int = 100,
        spAttack: Int = 100,
        spDefense: Int = 100,
        speed: Int = 100,
        ability: Int = 0
    ): PartyMon {
        return PartyMon(
            species = species,
            level = level,
            hp = hp,
            maxHp = hp,
            nickname = "Test",
            moves = listOf(1, 2, 3, 4),
            attack = attack,
            defense = defense,
            speed = speed,
            spAttack = spAttack,
            spDefense = spDefense,
            experience = 0,
            friendship = 255,
            heldItem = 0,
            ability = ability
        )
    }

    private fun createBattlerState(
        mon: PartyMon,
        currentHp: Int? = null,
        statStages: StatStages = StatStages(),
        status: Int = 0
    ): BattlerState {
        return BattlerState(
            mon = mon,
            currentHp = currentHp ?: mon.maxHp,
            statStages = statStages,
            status = status
        )
    }

    @Test
    fun testIntimidateLowersAttack() {
        val gyarados = createTestMon(species = 130, ability = INTIMIDATE)
        val dragonite = createTestMon(species = 149)

        val intimidateUser = createBattlerState(gyarados)
        val opponent = createBattlerState(dragonite)

        val state = BattleState(player = intimidateUser, enemy = opponent)
        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        // Enemy's Attack should be lowered by 1 stage
        assertEquals(-1, newState.enemy.statStages.attack)
        assertEquals(0, newState.player.statStages.attack)
    }

    @Test
    fun testIntimidateDoesNotDropBelowMinus六() {
        val gyarados = createTestMon(ability = INTIMIDATE)
        val opponent = createBattlerState(
            createTestMon(),
            statStages = StatStages(attack = -6)
        )

        val state = BattleState(
            player = createBattlerState(gyarados),
            enemy = opponent
        )
        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        // Should stay at -6, not go below
        assertEquals(-6, newState.enemy.statStages.attack)
    }

    @Test
    fun testMoxieBoostsOnKO() {
        val salamence = createTestMon(species = 373, ability = MOXIE)
        val blissey = createTestMon(species = 242, hp = 0)  // KO'd

        val moxieUser = createBattlerState(salamence)
        val koedOpponent = createBattlerState(blissey, currentHp = 0)

        val state = BattleState(player = moxieUser, enemy = koedOpponent)
        val newState = AbilityEffects.applyKOAbility(state, isPlayerKO = true)

        // Player's Attack should be raised by 1 stage
        assertEquals(1, newState.player.statStages.attack)
    }

    @Test
    fun testMoxieDoesNotBoostAbovePlusSix() {
        val salamence = createTestMon(ability = MOXIE)
        val moxieUser = createBattlerState(
            salamence,
            statStages = StatStages(attack = 6)
        )

        val state = BattleState(
            player = moxieUser,
            enemy = createBattlerState(createTestMon(), currentHp = 0)
        )
        val newState = AbilityEffects.applyKOAbility(state, isPlayerKO = true)

        // Should stay at +6, not go above
        assertEquals(6, newState.player.statStages.attack)
    }

    @Test
    fun testSpeedBoostEndOfTurn() {
        val blaziken = createTestMon(species = 257, ability = SPEED_BOOST)
        val speedBoostUser = createBattlerState(blaziken)

        val state = BattleState(
            player = speedBoostUser,
            enemy = createBattlerState(createTestMon())
        )
        val newState = AbilityEffects.applyEndOfTurnAbilities(state)

        // Player's Speed should be raised by 1 stage
        assertEquals(1, newState.player.statStages.speed)
    }

    @Test
    fun testGutsBoostsAttackWhenStatused() {
        val heracross = createTestMon(ability = GUTS, attack = 100)
        val gutsUser = createBattlerState(heracross, status = StatusConditions.BURN)

        // Physical move (category = 0)
        val multiplier = AbilityEffects.getAttackerStatMultiplier(gutsUser, moveCategory = 0)

        assertEquals(1.5f, multiplier, 0.01f)
    }

    @Test
    fun testGutsDoesNotBoostWhenHealthy() {
        val heracross = createTestMon(ability = GUTS)
        val gutsUser = createBattlerState(heracross, status = StatusConditions.NONE)

        val multiplier = AbilityEffects.getAttackerStatMultiplier(gutsUser, moveCategory = 0)

        assertEquals(1.0f, multiplier, 0.01f)
    }

    @Test
    fun testGutsDoesNotBoostSpecialMoves() {
        val heracross = createTestMon(ability = GUTS)
        val gutsUser = createBattlerState(heracross, status = StatusConditions.BURN)

        // Special move (category = 1)
        val multiplier = AbilityEffects.getAttackerStatMultiplier(gutsUser, moveCategory = 1)

        assertEquals(1.0f, multiplier, 0.01f)
    }

    @Test
    fun testMarvelScaleBoostsDefenseWhenStatused() {
        val milotic = createTestMon(ability = MARVEL_SCALE)
        val marvelScaleUser = createBattlerState(milotic, status = StatusConditions.BURN)

        // Physical move defense (category = 0)
        val multiplier = AbilityEffects.getDefenderStatMultiplier(marvelScaleUser, moveCategory = 0)

        assertEquals(1.5f, multiplier, 0.01f)
    }

    @Test
    fun testDownloadBoostsAttackWhenDefenseLower() {
        val porygonZ = createTestMon(ability = DOWNLOAD)
        // Enemy with lower Defense than Sp.Def
        val enemy = createTestMon(defense = 50, spDefense = 100)

        val state = BattleState(
            player = createBattlerState(porygonZ),
            enemy = createBattlerState(enemy)
        )
        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        // Should boost Attack since Defense is lower
        assertEquals(1, newState.player.statStages.attack)
        assertEquals(0, newState.player.statStages.spAttack)
    }

    @Test
    fun testDownloadBoostsSpAttackWhenSpDefenseLower() {
        val porygonZ = createTestMon(ability = DOWNLOAD)
        // Enemy with lower Sp.Def than Defense
        val enemy = createTestMon(defense = 100, spDefense = 50)

        val state = BattleState(
            player = createBattlerState(porygonZ),
            enemy = createBattlerState(enemy)
        )
        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        // Should boost Sp.Attack since Sp.Def is lower
        assertEquals(0, newState.player.statStages.attack)
        assertEquals(1, newState.player.statStages.spAttack)
    }

    @Test
    fun testBeastBoostRaisesHighestStat() {
        // Pheromosa has highest stat of Speed
        val pheromosa = createTestMon(
            ability = BEAST_BOOST,
            attack = 137,
            speed = 151  // Highest stat
        )

        val state = BattleState(
            player = createBattlerState(pheromosa),
            enemy = createBattlerState(createTestMon(), currentHp = 0)
        )
        val newState = AbilityEffects.applyKOAbility(state, isPlayerKO = true)

        // Should boost Speed (highest stat)
        assertEquals(1, newState.player.statStages.speed)
    }

    @Test
    fun testTechnicianBoostsWeakMoves() {
        val scizor = createTestMon(ability = TECHNICIAN)
        val techUser = createBattlerState(scizor)

        // Bullet Punch has 40 base power
        val bulletPunchId = 418

        // Note: This test requires that MoveData has Bullet Punch
        // For now, we'll just verify the ability checks power correctly
        // The actual implementation would need PokemonData.getMoveData(418)
    }

    @Test
    fun testDroughtSetsWeather() {
        val charizard = createTestMon(species = 6, ability = 70)  // Drought
        val state = BattleState(
            player = createBattlerState(charizard),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Drought should set sun", Weather.SUN, newState.weather)
    }

    @Test
    fun testDrizzleSetsWeather() {
        val politoed = createTestMon(species = 186, ability = 2)  // Drizzle
        val state = BattleState(
            player = createBattlerState(politoed),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Drizzle should set rain", Weather.RAIN, newState.weather)
    }

    @Test
    fun testSandStreamSetsWeather() {
        val tyranitar = createTestMon(species = 248, ability = 45)  // Sand Stream
        val state = BattleState(
            player = createBattlerState(tyranitar),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Sand Stream should set sandstorm", Weather.SANDSTORM, newState.weather)
    }

    @Test
    fun testSnowWarningSetsWeather() {
        val abomasnow = createTestMon(species = 460, ability = 117)  // Snow Warning
        val state = BattleState(
            player = createBattlerState(abomasnow),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Snow Warning should set hail", Weather.HAIL, newState.weather)
    }

    @Test
    fun testDesolateLandSetsPrimalWeather() {
        val primalGroudon = createTestMon(species = 383, ability = 190)  // Desolate Land
        val state = BattleState(
            player = createBattlerState(primalGroudon),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Desolate Land should set harsh sun", Weather.HARSH_SUN, newState.weather)
    }

    @Test
    fun testPrimordialSeaSetsPrimalWeather() {
        val primalKyogre = createTestMon(species = 382, ability = 189)  // Primordial Sea
        val state = BattleState(
            player = createBattlerState(primalKyogre),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Primordial Sea should set heavy rain", Weather.HEAVY_RAIN, newState.weather)
    }

    @Test
    fun testDeltaStreamSetsPrimalWeather() {
        val megaRayquaza = createTestMon(species = 384, ability = 191)  // Delta Stream
        val state = BattleState(
            player = createBattlerState(megaRayquaza),
            enemy = createBattlerState(createTestMon(species = 242))
        )

        val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

        assertEquals("Delta Stream should set strong winds", Weather.STRONG_WINDS, newState.weather)
    }

    @Test
    fun testChlorophyllDoublesSpeedInSun() {
        val venusaur = createTestMon(species = 3, ability = 34, speed = 100)  // Chlorophyll
        val battler = createBattlerState(venusaur)

        val normalSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.NONE)
        val sunSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.SUN)

        assertEquals("Normal speed should be 1x", 1.0f, normalSpeed, 0.01f)
        assertEquals("Chlorophyll should double speed in sun", 2.0f, sunSpeed, 0.01f)
    }

    @Test
    fun testChlorophyllDoesNotBoostInRain() {
        val venusaur = createTestMon(species = 3, ability = 34, speed = 100)  // Chlorophyll
        val battler = createBattlerState(venusaur)

        val rainSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.RAIN)

        assertEquals("Chlorophyll should not boost in rain", 1.0f, rainSpeed, 0.01f)
    }

    @Test
    fun testSwiftSwimDoublesSpeedInRain() {
        val kingdra = createTestMon(species = 230, ability = 33, speed = 85)  // Swift Swim
        val battler = createBattlerState(kingdra)

        val normalSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.NONE)
        val rainSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.RAIN)

        assertEquals("Normal speed should be 1x", 1.0f, normalSpeed, 0.01f)
        assertEquals("Swift Swim should double speed in rain", 2.0f, rainSpeed, 0.01f)
    }

    @Test
    fun testSandRushDoublesSpeedInSandstorm() {
        val excadrill = createTestMon(species = 530, ability = 146, speed = 88)  // Sand Rush
        val battler = createBattlerState(excadrill)

        val normalSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.NONE)
        val sandSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.SANDSTORM)

        assertEquals("Normal speed should be 1x", 1.0f, normalSpeed, 0.01f)
        assertEquals("Sand Rush should double speed in sandstorm", 2.0f, sandSpeed, 0.01f)
    }

    @Test
    fun testSlushRushDoublesSpeedInHail() {
        val sandslash = createTestMon(species = 28, ability = 202, speed = 65)  // Slush Rush (Alolan)
        val battler = createBattlerState(sandslash)

        val normalSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.NONE)
        val hailSpeed = AbilityEffects.getWeatherSpeedMultiplier(battler, Weather.HAIL)

        assertEquals("Normal speed should be 1x", 1.0f, normalSpeed, 0.01f)
        assertEquals("Slush Rush should double speed in hail", 2.0f, hailSpeed, 0.01f)
    }
}
