package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.data.SpotData
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarFlag
import org.bukkit.boss.BarStyle
import org.bukkit.boss.KeyedBossBar
import org.bukkit.entity.Player

object BossbarHandler {
  private val TIME_PURCHASING_BOSSBAR_KEY = { type: Config.WorldType ->
    NamespacedKey(ZoneRush.getInstance(), "zone_rush_${type.worldName}_time_purchasing")
  }
  private val TIME_PLAYING_BOSSBAR_KEY = { type: Config.WorldType ->
    NamespacedKey(ZoneRush.getInstance(), "zone_rush_${type.worldName}_time_playing")
  }
  private val SPOT_BOSSBAR_KEY = { spot: SpotData ->
    NamespacedKey(ZoneRush.getInstance(), "zone_rush_${spot.id}_spot")
  }
  private val purchaseBossBars = mutableMapOf<String, KeyedBossBar>()
  private val gameLeftTimeBossBars = mutableMapOf<String, KeyedBossBar>()
  private val spotGaugeBossBars = mutableMapOf<String, KeyedBossBar>()

  private val PURCHASE_BOSSBAR_GENERATOR: (Config.WorldType, Double) -> KeyedBossBar = { type, progress ->
    val key = TIME_PURCHASING_BOSSBAR_KEY(type)
    val bar =
      Bukkit.createBossBar(
        key,
        "武器購入の残り時間",
        BarColor.PINK,
        BarStyle.SOLID,
        BarFlag.PLAY_BOSS_MUSIC
      )
    bar.progress = progress
    purchaseBossBars[type.worldName] = bar
    bar
  }

  private val PURCHASE_BOSSBAR_DELETOR: (Config.WorldType) -> Unit = {
    val bossbar = purchaseBossBars[it.worldName]
    if (bossbar != null) {
      bossbar.removeAll()
      bossbar.isVisible = false
      Bukkit.removeBossBar(bossbar.key)
      purchaseBossBars.remove(it.worldName)
    }
  }

  private val GAME_LEFT_TIME_BOSSBAR_GENERATOR: (Config.WorldType, Double) -> KeyedBossBar = { type, progress ->
    val key = TIME_PLAYING_BOSSBAR_KEY(type)
    val bar =
      Bukkit.createBossBar(
        key,
        "残り時間",
        BarColor.GREEN,
        BarStyle.SOLID,
        BarFlag.PLAY_BOSS_MUSIC
      )
    bar.progress = progress
    gameLeftTimeBossBars[type.worldName] = bar
    bar
  }

  private val GAME_LEFT_TIME_BOSSBAR_DELETOR: (Config.WorldType) -> Unit = {
    val bossbar = gameLeftTimeBossBars[it.worldName]
    if (bossbar != null) {
      bossbar.removeAll()
      bossbar.isVisible = false
      Bukkit.removeBossBar(bossbar.key)
      gameLeftTimeBossBars.remove(it.worldName)
    }
  }

  private val SPOT_BOSSBAR_GENERATOR: (SpotData, Double) -> KeyedBossBar = { spot, progress ->
    val key = SPOT_BOSSBAR_KEY(spot)
    val bar =
      Bukkit.createBossBar(
        key,
        spot.id,
        BarColor.WHITE,
        BarStyle.SOLID
      )
    bar.progress = progress
    spotGaugeBossBars[spot.id] = bar
    bar
  }

  private val SPOT_BOSSBAR_DELETOR: (SpotData) -> Unit = {
    val bossbar = spotGaugeBossBars[it.id]
    if (bossbar != null) {
      bossbar.removeAll()
      bossbar.isVisible = false
      Bukkit.removeBossBar(bossbar.key)
      spotGaugeBossBars.remove(it.id)
    }
  }

  fun setup(worldType: Config.WorldType) {
    PURCHASE_BOSSBAR_GENERATOR(worldType, 1.0)
    GAME_LEFT_TIME_BOSSBAR_GENERATOR(worldType, 1.0)
    worldType.spots.forEach {
      SPOT_BOSSBAR_GENERATOR(it, 1.0)
    }
  }

  fun stop(worldType: Config.WorldType) {
    PURCHASE_BOSSBAR_DELETOR(worldType)
    GAME_LEFT_TIME_BOSSBAR_DELETOR(worldType)
    worldType.spots.forEach {
      SPOT_BOSSBAR_DELETOR(it)
    }
  }

  // purchase
  fun onPurchasePhaseStart(
    players: List<Player>,
    gameType: Config.WorldType
  ) {
    val bossbar = purchaseBossBars[gameType.worldName]!!
    players.forEach {
      bossbar.addPlayer(it)
    }
    bossbar.isVisible = true
  }

  fun updatePurchasingTimer(
    remain: Int,
    gameType: Config.WorldType
  ) {
    purchaseBossBars.computeIfPresent(gameType.worldName) { _, bar ->
      bar.progress = (remain.toDouble() / Config.buyPhaseDuration.toDouble())
      bar
    }
  }

  fun onPurchasePhaseStop(gameType: Config.WorldType) {
    PURCHASE_BOSSBAR_DELETOR(gameType)
  }

  // end purchase

  // game
  fun onGameStart(
    players: List<Player>,
    gameType: Config.WorldType
  ) {
    val bossbar = gameLeftTimeBossBars[gameType.worldName]!!
    players.forEach {
      bossbar.addPlayer(it)
    }
    bossbar.isVisible = true
  }

  fun updatePlayingTimer(
    remain: Int,
    gameType: Config.WorldType,
    players: List<Player>
  ) {
    gameLeftTimeBossBars.computeIfPresent(gameType.worldName) { _, bar ->
      bar.progress = (remain.toDouble() / Config.roundTimeoutDuration.toDouble())
      players.forEach { bar.addPlayer(it) }
      bar
    }
  }

  fun onGameStop(gameType: Config.WorldType) {
    GAME_LEFT_TIME_BOSSBAR_DELETOR(gameType)
  }

  fun leaveSpot(
    spot: SpotData,
    player: Player
  ) {
    spotGaugeBossBars.computeIfPresent(spot.id) { _, bar ->
      bar.removePlayer(player)
      bar
    }
  }

  fun updateSpotGauge(
    spot: SpotData,
    color: BarColor? = null,
    progress: Double = spot.currentGauge / 150
  ) {
    spotGaugeBossBars.computeIfPresent(spot.id) { _, bar ->
      bar.progress = progress
      bar.color = color ?: bar.color
      bar
    }
  }

  fun enterSpot(
    spot: SpotData,
    player: Player
  ) {
    spotGaugeBossBars.computeIfPresent(spot.id) { _, bar ->
      bar.addPlayer(player)
      bar
    }
  }

  fun shutdown() {
    Bukkit
      .getBossBars()
      .forEach {
        if (it.key.namespace == "zonerush") {
          it.isVisible = false
          it.removeAll()
        }
      }
  }
}
