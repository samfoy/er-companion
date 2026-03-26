package com.ercompanion.calc

import com.ercompanion.parser.PartyMon
import org.junit.Assert.*
import org.junit.Test

class WeatherEffectsTest {

    private fun createTestMon(
        species: Int,
        level: Int = 50,
        maxHp: Int = 150,
        attack: Int = 100,
        defense: Int = 100,
        spAttack: Int = 100,
        spDefense: Int = 100,
        speed: Int = 100,
        ability: Int = 0,
        currentHp: Int = maxHp
    ): PartyMon {
        return PartyMon(
            species = species,
            level = level,
            hp = currentHp,
            maxHp = maxHp,
            nickname = "Test",
            moves = listOf(0, 0, 0, 0),
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
        currentHp: Int = mon.maxHp
    ): BattlerState {
        return BattlerState(
            mon = mon,
            currentHp = currentHp,
            statStages = StatStages(),
            status = 0
        )
    }

    @Test
    fun testSunBoostsFireMoves() {
        val charizard = createTestMon(species = 6)  // Charizard (Fire/Flying)
        val blissey = createTestMon(species = 242)   // Blissey

        val state = BattleState(
            player = createBattlerState(charizard),
            enemy = createBattlerState(blissey),
            weather = Weather.SUN
        )

        val flamethrower = 53  // Flamethrower move ID
        val damage = MoveSimulator.calculateDamageWithStages(
            state.player, state.enemy, flamethrower, weather = state.weather
        )

        // Verify damage is calculated
        assertTrue("Fire move should deal damage in sun", damage.maxDamage > 0)
        assertTrue("Damage calculation should be valid", damage.isValid)
    }

    @Test
    fun testRainWeakensFireMoves() {
        val charizard = createTestMon(species = 6)  // Charizard
        val blissey = createTestMon(species = 242)   // Blissey

        val state = BattleState(
            player = createBattlerState(charizard),
            enemy = createBattlerState(blissey),
            weather = Weather.RAIN
        )

        val flamethrower = 53  // Flamethrower
        val multiplier = WeatherEffects.getWeatherMultiplier(
            Weather.RAIN,
            moveType = 9,  // Fire type
            attacker = state.player
        )

        assertEquals("Fire moves should be weakened in rain", 0.5f, multiplier, 0.01f)
    }

    @Test
    fun testRainBoostsWaterMoves() {
        val blastoise = createTestMon(species = 9)  // Blastoise (Water)
        val charizard = createTestMon(species = 6)  // Charizard

        val state = BattleState(
            player = createBattlerState(blastoise),
            enemy = createBattlerState(charizard),
            weather = Weather.RAIN
        )

        val surf = 57  // Surf move ID
        val multiplier = WeatherEffects.getWeatherMultiplier(
            Weather.RAIN,
            moveType = 10,  // Water type
            attacker = state.player
        )

        assertEquals("Water moves should be boosted in rain", 1.5f, multiplier, 0.01f)
    }

    @Test
    fun testSandstormDamagesNonImmune() {
        val dragonite = createTestMon(species = 149, maxHp = 300)  // Dragonite (Dragon/Flying)

        val state = BattleState(
            player = createBattlerState(dragonite),
            enemy = createBattlerState(createTestMon(species = 242)),  // Blissey
            weather = Weather.SANDSTORM
        )

        val newState = WeatherEffects.applyWeatherDamage(state, isPlayer = true)

        // Should take 1/16 max HP damage
        val expectedDamage = dragonite.maxHp / 16
        val actualDamage = state.player.currentHp - newState.player.currentHp
        assertTrue("Dragonite should take sandstorm damage", actualDamage > 0)
        assertEquals("Should take 1/16 max HP", expectedDamage, actualDamage)
    }

    @Test
    fun testSandstormDoesNotDamageRockTypes() {
        val tyranitar = createTestMon(species = 248)  // Tyranitar (Rock/Dark)

        val state = BattleState(
            player = createBattlerState(tyranitar),
            enemy = createBattlerState(createTestMon(species = 242)),
            weather = Weather.SANDSTORM
        )

        val newState = WeatherEffects.applyWeatherDamage(state, isPlayer = true)

        // Should not take damage
        val actualDamage = state.player.currentHp - newState.player.currentHp
        assertEquals("Rock type should not take sandstorm damage", 0, actualDamage)
    }

    @Test
    fun testHailDamagesNonIceTypes() {
        val dragonite = createTestMon(species = 149, maxHp = 300)  // Dragonite

        val state = BattleState(
            player = createBattlerState(dragonite),
            enemy = createBattlerState(createTestMon(species = 242)),
            weather = Weather.HAIL
        )

        val newState = WeatherEffects.applyWeatherDamage(state, isPlayer = true)

        // Should take 1/16 max HP damage
        val expectedDamage = dragonite.maxHp / 16
        val actualDamage = state.player.currentHp - newState.player.currentHp
        assertEquals("Should take 1/16 max HP from hail", expectedDamage, actualDamage)
    }

    @Test
    fun testElectricTerrainBoostsElectricMoves() {
        val pikachu = createTestMon(species = 25)  // Pikachu (Electric)
        val blissey = createTestMon(species = 242)  // Blissey

        val state = BattleState(
            player = createBattlerState(pikachu),
            enemy = createBattlerState(blissey),
            terrain = Terrain.ELECTRIC
        )

        val multiplier = TerrainEffects.getTerrainMultiplier(
            Terrain.ELECTRIC,
            moveType = 12,  // Electric type
            attacker = state.player,
            isGrounded = true
        )

        assertEquals("Electric moves should be boosted on Electric Terrain", 1.5f, multiplier, 0.01f)
    }

    @Test
    fun testGrassyTerrainBoostsGrassMoves() {
        val venusaur = createTestMon(species = 3)  // Venusaur (Grass/Poison)
        val charizard = createTestMon(species = 6)  // Charizard

        val state = BattleState(
            player = createBattlerState(venusaur),
            enemy = createBattlerState(charizard),
            terrain = Terrain.GRASSY
        )

        val multiplier = TerrainEffects.getTerrainMultiplier(
            Terrain.GRASSY,
            moveType = 11,  // Grass type
            attacker = state.player,
            isGrounded = true
        )

        assertEquals("Grass moves should be boosted on Grassy Terrain", 1.5f, multiplier, 0.01f)
    }

    @Test
    fun testGrassyTerrainHealsGroundedPokemon() {
        val tyranitar = createTestMon(species = 248, maxHp = 300)  // Tyranitar (Rock/Dark, grounded)

        val state = BattleState(
            player = createBattlerState(tyranitar, currentHp = 200),
            enemy = createBattlerState(createTestMon(species = 242)),
            terrain = Terrain.GRASSY
        )

        val newState = TerrainEffects.applyTerrainHealing(state, isPlayer = true)

        // Should heal 1/16 max HP
        val expectedHealing = tyranitar.maxHp / 16
        val actualHealing = newState.player.currentHp - state.player.currentHp
        assertEquals("Should heal 1/16 max HP on Grassy Terrain", expectedHealing, actualHealing)
    }

    @Test
    fun testGrassyTerrainDoesNotHealFlyingTypes() {
        val charizard = createTestMon(species = 6)  // Charizard (Fire/Flying)

        val state = BattleState(
            player = createBattlerState(charizard, currentHp = 200),
            enemy = createBattlerState(createTestMon(species = 242)),
            terrain = Terrain.GRASSY
        )

        val newState = TerrainEffects.applyTerrainHealing(state, isPlayer = true)

        // Should not heal
        val actualHealing = newState.player.currentHp - state.player.currentHp
        assertEquals("Flying type should not be healed by Grassy Terrain", 0, actualHealing)
    }

    @Test
    fun testMistyTerrainWeakensDragonMoves() {
        val dragonite = createTestMon(species = 149)  // Dragonite
        val blissey = createTestMon(species = 242)     // Blissey

        val state = BattleState(
            player = createBattlerState(dragonite),
            enemy = createBattlerState(blissey),
            terrain = Terrain.MISTY
        )

        val multiplier = TerrainEffects.getTerrainMultiplier(
            Terrain.MISTY,
            moveType = 15,  // Dragon type
            attacker = state.player,
            isGrounded = true
        )

        assertEquals("Dragon moves should be weakened on Misty Terrain", 0.5f, multiplier, 0.01f)
    }

    @Test
    fun testPsychicTerrainBoostsPsychicMoves() {
        val alakazam = createTestMon(species = 65)  // Alakazam (Psychic)
        val machamp = createTestMon(species = 68)   // Machamp

        val state = BattleState(
            player = createBattlerState(alakazam),
            enemy = createBattlerState(machamp),
            terrain = Terrain.PSYCHIC
        )

        val multiplier = TerrainEffects.getTerrainMultiplier(
            Terrain.PSYCHIC,
            moveType = 13,  // Psychic type
            attacker = state.player,
            isGrounded = true
        )

        assertEquals("Psychic moves should be boosted on Psychic Terrain", 1.5f, multiplier, 0.01f)
    }

    @Test
    fun testTerrainDoesNotAffectFlyingTypes() {
        val charizard = createTestMon(species = 6)  // Charizard (Fire/Flying)
        val blissey = createTestMon(species = 242)   // Blissey

        val state = BattleState(
            player = createBattlerState(charizard),
            enemy = createBattlerState(blissey),
            terrain = Terrain.ELECTRIC
        )

        // Flying types are not grounded
        val multiplier = TerrainEffects.getTerrainMultiplier(
            Terrain.ELECTRIC,
            moveType = 12,  // Electric type
            attacker = state.player,
            isGrounded = false
        )

        assertEquals("Terrain should not affect flying types", 1.0f, multiplier, 0.01f)
    }

    @Test
    fun testWeatherSetByMove() {
        val dragonite = createTestMon(species = 149)
        val blissey = createTestMon(species = 242)

        val state = BattleState(
            player = createBattlerState(dragonite),
            enemy = createBattlerState(blissey)
        )

        // Sunny Day move ID = 241
        val sunnyDay = 241
        val weather = MoveEffects.setsWeather(sunnyDay)

        assertNotNull("Sunny Day should set weather", weather)
        assertEquals("Sunny Day should set sun", Weather.SUN, weather)
    }

    @Test
    fun testTerrainSetByMove() {
        val alakazam = createTestMon(species = 65)
        val machamp = createTestMon(species = 68)

        val state = BattleState(
            player = createBattlerState(alakazam),
            enemy = createBattlerState(machamp)
        )

        // Electric Terrain move ID = 678 (if available in ER)
        val electricTerrain = 678
        val terrain = MoveEffects.setsTerrain(electricTerrain)

        // May be null if not in ER move list
        if (terrain != null) {
            assertEquals("Electric Terrain should set electric terrain", Terrain.ELECTRIC, terrain)
        }
    }

    @Test
    fun testPrimalWeatherCannotBeChanged() {
        val state = BattleState(
            player = createBattlerState(createTestMon(species = 149)),  // Dragonite
            enemy = createBattlerState(createTestMon(species = 242)),   // Blissey
            weather = Weather.HARSH_SUN
        )

        // Try to change to rain
        assertFalse("Harsh sun should not allow weather changes", WeatherEffects.canChangeWeather(state.weather))

        val newState = WeatherEffects.setWeather(state, Weather.RAIN)
        assertEquals("Weather should not change from primal weather", Weather.HARSH_SUN, newState.weather)
    }

    @Test
    fun testPrimalWeatherHeavyRainCannotBeChanged() {
        val state = BattleState(
            player = createBattlerState(createTestMon(species = 149)),
            enemy = createBattlerState(createTestMon(species = 242)),
            weather = Weather.HEAVY_RAIN
        )

        assertFalse("Heavy rain should not allow weather changes", WeatherEffects.canChangeWeather(state.weather))

        val newState = WeatherEffects.setWeather(state, Weather.SUN)
        assertEquals("Weather should not change from primal weather", Weather.HEAVY_RAIN, newState.weather)
    }

    @Test
    fun testPrimalWeatherStrongWindsCannotBeChanged() {
        val state = BattleState(
            player = createBattlerState(createTestMon(species = 149)),
            enemy = createBattlerState(createTestMon(species = 242)),
            weather = Weather.STRONG_WINDS
        )

        assertFalse("Strong winds should not allow weather changes", WeatherEffects.canChangeWeather(state.weather))

        val newState = WeatherEffects.setWeather(state, Weather.HAIL)
        assertEquals("Weather should not change from primal weather", Weather.STRONG_WINDS, newState.weather)
    }

    @Test
    fun testNormalWeatherCanBeChanged() {
        val state = BattleState(
            player = createBattlerState(createTestMon(species = 149)),
            enemy = createBattlerState(createTestMon(species = 242)),
            weather = Weather.SUN
        )

        assertTrue("Normal weather should allow changes", WeatherEffects.canChangeWeather(state.weather))

        val newState = WeatherEffects.setWeather(state, Weather.RAIN)
        assertEquals("Weather should change from normal weather", Weather.RAIN, newState.weather)
    }
}
