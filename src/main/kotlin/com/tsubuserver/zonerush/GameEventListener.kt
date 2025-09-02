package com.tsubuserver.zonerush

import com.github.peco2282.paperkit.component
import com.github.peco2282.paperkit.on
import com.tsubuserver.playerDataLib.api.APILib
import com.tsubuserver.playerDataLib.api.StatsBuilder
import com.tsubuserver.zonerush.data.GameTeam
import com.tsubuserver.zonerush.data.GameTeam.*
import com.tsubuserver.zonerush.data.SpotData
import com.tsubuserver.zonerush.manager.game.GameInstance
import com.tsubuserver.zonerush.manager.game.GameManager
import com.tsubuserver.zonerush.manager.matching.MatchingManager
import com.tsubuserver.zonerush.task.CaptureHealTask
import io.papermc.paper.event.player.PlayerPickItemEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.Location
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.function.Consumer

object GameEventListener : Listener {
  private val plugin = ZoneRush.getInstance()

  init {
    plugin.on<PlayerJoinEvent> {
      val uuid = it.player.uniqueId
      val stats = APILib.statsInstance.getPlayerStats(uuid)
      if (stats == null) {
        it.player.server.sendMessage(component {
          append("${it.player.name}が初参加です") {
            color(NamedTextColor.GREEN)
          }
        })
        APILib.statsInstance.savePlayerStats(
          StatsBuilder.builder(uuid, Consumer {it ->
            val now = System.currentTimeMillis()
            it.firstLogin = now
            it.lastLogin = now
          }
        ))
        return@on
      }
      APILib.statsInstance.editPresent(uuid) { it ->
        it.lastLogin = System.currentTimeMillis()
        it
      }
    }
//    plugin.on<PlayerMoveEvent> {
//      val player = it.player
//      val game = GameManager.getGameInstance(player.uniqueId) ?: return@on
//
//      it.isCancelled = !isMovementAllowed(game.gamePhase)
//    }

    plugin.on<PlayerMoveEvent> {
      val player = it.player
      val fromLoc = it.from
      val toLoc = it.to

      val game: GameInstance = GameManager.getGameInstance(player.uniqueId) ?: return@on

      processSpotInteraction(fromLoc, toLoc, player, game)
    }

//    plugin.on<PlayerDeathEvent> {
//      val player = it.entity
//      val game: GameInstance = GameManager.getGameInstance(player.uniqueId) ?: return@on
//      val world = Bukkit.getWorld(game.worldType.worldName) ?: return@on
//      it.player.teleport(game.worldType.defaultLocation.apply { this.world = world })
//    }

    plugin.on<BlockBreakEvent> {
      val player = it.player
      GameManager.getGameInstance(player.uniqueId) ?: return@on
      it.isCancelled = true
    }

    plugin.on<PlayerDropItemEvent> {
      val player = it.player
      GameManager.getGameInstance(player.uniqueId) ?: return@on
      it.isCancelled = true
    }

    plugin.on<PlayerPickItemEvent> {
      val player = it.player
      GameManager.getGameInstance(player.uniqueId) ?: return@on
      it.isCancelled = true
    }

    plugin.on<PlayerQuitEvent> {
      val matching = MatchingManager.getMatchingInstance(it.player.uniqueId) ?: return@on
      matching.detachPlayerMatching(it.player)
      UIHandler.removeGameInfoBar(listOf(it.player))
      it.quitMessage(
        component {
          append(matching.gameType.name) {
            color(NamedTextColor.GOLD)
          }
          append("の参加者から退出しました") {
            color(NamedTextColor.RED)
          }
        }
      )
    }

    // TODO: 2025/8/16 ゲーム中の購入時間中、インベントリを開くとショップ開く
//    plugin.on<InventoryOpenEvent> {
//      val player = it.player as? Player ?: return@on
//      GameManager.getGameInstance(player.uniqueId) ?: return@on
//      if (it.inventory.type == InventoryType.PLAYER) return@on
//      player.openInventory
//      GuiHandler.openShop(player)
//    }
  }

  private fun isMovementAllowed(phase: GameManager.GamePhase) = when (phase) {
    GameManager.GamePhase.WAITING_FOR_PLAYERS,
    GameManager.GamePhase.ROUND_ACTIVE,
    GameManager.GamePhase.TELEPORTED_WAITING_PLAYERS,
    GameManager.GamePhase.GAME_END -> true

    else -> false
  }

  private fun handlePlayerEnterSpot(
    player: Player,
    playerTeam: GameTeam,
    spot: SpotData,
    inst: GameInstance
  ) {
    BossbarHandler.enterSpot(spot, player)
    if (inst.getEnemyPlayersInSpot(spot, playerTeam) > 0) {
      spot.currentTask?.cancel()
      spot.currentTask = null
      player.sendActionBar("§c敵がいます！奪取/回復できません".component())
      return
    }

    val existingTask = spot.currentTask as? CaptureHealTask
    if (existingTask?.executingTeam == playerTeam) {
      return
    }
    spot.currentTask?.cancel()

    val newTask = CaptureHealTask(spot, playerTeam, inst, player)
    newTask.runTaskTimer(plugin, 0L, 20L)
    spot.currentTask = newTask

    val message = if (spot.currentTeam == playerTeam) {
      "§a回復中: ${spot.id} (${String.format("%.1f", spot.currentGauge)}%)"
    } else {
      "§e奪取中: ${spot.id} (${String.format("%.1f", spot.currentGauge)}%)"
    }
    player.sendActionBar(message.component())
  }

  private fun handlePlayerLeaveSpot(
    player: Player,
    playerTeam: GameTeam,
    spot: SpotData,
    inst: GameInstance,
    needAnnounce: Boolean = true
  ) {
    BossbarHandler.leaveSpot(spot, player)
    if (inst.getFriendlyPlayersInSpot(spot, playerTeam) == 0) {
      spot.currentTask?.cancel()
      spot.currentTask = null
      if (needAnnounce)
        player.sendActionBar("§7陣地から離れました。ゲージの進行/回復が停止しました。".component())
    } else {
      if (needAnnounce)
        player.sendActionBar("§7陣地から離れました。".component())
    }
  }

  @EventHandler
  fun whenPlayerDies(event: PlayerDeathEvent) {
    val player = event.entity
//    if (player.gameMode != GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) return
    val game: GameInstance = GameManager.getGameInstance(player.uniqueId) ?: return
    val team = game.getTeam(player.uniqueId) ?: return

    if (game.gamePhase != GameManager.GamePhase.ROUND_ACTIVE) return
    val world = Bukkit.getWorld(game.worldType.worldName) ?: return
    Bukkit.getServer().scheduler.runTaskLater(
      plugin,
      Runnable {
        player.teleport(game.worldType.defaultLocation.apply { this.world = world })
        player.gameMode = GameMode.SPECTATOR
      },
      5.toLong()
    )
    val loc = when (team) {
      RED -> game.worldType.defaultRedLocation
      BLUE -> game.worldType.defaultBlueLocation
      NEUTRAL -> game.worldType.defaultLocation
    }
    // Respawn player after 7 seconds. count down for last 3 seconds before respawn. plugin.api?.respawnPlayer(player) will respawn the player

    game.worldType.spots.forEach {
      handlePlayerLeaveSpot(player, team, it, game, false)
      BossbarHandler.leaveSpot(it, player)
    }

    object : BukkitRunnable() {
      var count = 5

      override fun run() {
        if (count == 0) {
          player.teleport(loc.apply { this.world = world })
          player.gameMode = GameMode.SURVIVAL
          game.addImmunePlayer(player.uniqueId)
          player.sendMessage("§a[無敵] リスポーン後5秒間無敵です") // 任意でメッセージ
          // 6秒後に無敵状態を解除するタスクをスケジュール
          runTaskLater(6) { //Doncha0003: 無敵時間を3 → 6に変更
            if (game.isImmune(player.uniqueId)) { // 念のためまだ無敵状態か確認
              game.removeImmunePlayer(player.uniqueId)
              // プレイヤーがまだオンラインならメッセージを送る
              if (player.isOnline) {
                player.sendMessage("§e[無敵] 無敵時間が終了しました。")
              }
            }
          }
          cancel()
        } else if (count <= 3) {
          player.sendTitle(count.toString(), "復活まで", 2, 20, 20)
        }
        count--
      }
    }.runTaskTimer(plugin, 0, 20)
  }

  @EventHandler
  fun onPlayerDamageAfterDead(event: EntityDamageEvent) {
    val player = event.entity as? Player ?: return
    val game: GameInstance = GameManager.getGameInstance(player.uniqueId) ?: return
    if (game.gamePhase != GameManager.GamePhase.ROUND_ACTIVE) return

    // プレイヤーが無敵リストに含まれているか確認
    if (game.isImmune(player.uniqueId)) {
      event.isCancelled = true
      player.sendMessage("§cあなたは現在無敵です！")
      return
    }
  }

  @EventHandler
  fun onFriendlyFire(event: EntityDamageEvent) {
    val player = event.entity as? Player ?: return
    val game: GameInstance = GameManager.getGameInstance(player.uniqueId) ?: return

    @Suppress("UnstableApiUsage") val source = event.damageSource.causingEntity as? Player ?: return
    if (game.getTeam(player.uniqueId) == game.getTeam(source.uniqueId)) {
      event.isCancelled = true
      return
    }
  }

  private fun processSpotInteraction(fromLoc: Location, toLoc: Location, player: Player, game: GameInstance) {
    if (player.gameMode == GameMode.SPECTATOR || game.gamePhase != GameManager.GamePhase.ROUND_ACTIVE) return

    game.worldType.spots.forEach { spot ->
      val wasInside = spot.isInside(fromLoc)
      val isInside = spot.isInside(toLoc)
      val team = game.getTeam(player.uniqueId) ?: return@forEach

      when {
        !wasInside && isInside -> handlePlayerEnterSpot(player, team, spot, game)
        wasInside && !isInside -> handlePlayerLeaveSpot(player, team, spot, game)
        wasInside -> handlePlayerEnterSpot(player, team, spot, game)
      }
    }
  }
}
