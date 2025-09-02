package com.tsubuserver.zonerush.manager.matching

import com.tsubuserver.zonerush.Config
import com.tsubuserver.zonerush.UIHandler
import com.tsubuserver.zonerush.ZoneRush
import com.tsubuserver.zonerush.asPlayer
import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.manager.game.GameManager
import com.tsubuserver.zonerush.runTaskLater
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class MatchingInstance(
  val gameType: GameType
) {
  companion object {
    private val plugin = ZoneRush.getInstance()
  }

  private val matchingPlayers: MutableSet<UUID> = mutableSetOf()
  private var matchingTaskId = -1
  val min = gameType.min
  val max = gameType.max
  var isCompleted = false
  var lastTime = 0

  fun getPlayers(): Set<UUID> = matchingPlayers.toSet()

  private fun checkInvalidPlayer() {
    matchingPlayers.forEach {
      if (it.asPlayer()?.isOnline != true) {
        matchingPlayers.remove(it)
        plugin.logger.info("Player $it is disconnected. Matching list is cleared.")
        UIHandler.updateGameInfoBar(this)
        return@forEach
      }
    }
  }

  fun addPlayerToMatching(player: Player) {
    if (!matchingPlayers.contains(player.uniqueId)) {
      matchingPlayers.add(player.uniqueId)
      player.sendMessage("§aマッチング待機リストに追加されました。")
      UIHandler.updateGameInfoBar(this)

      if (matchingPlayers.size == 1 && matchingTaskId == -1) {
        startMatchingTimer()
      }
    } else {
      player.sendMessage("§cあなたはすでにマッチング待機中です。")
    }
  }

  fun detachPlayerMatching(player: Player) {
    if (matchingPlayers.contains(player.uniqueId)) {
      matchingPlayers.remove(player.uniqueId)
      player.sendMessage("§aマッチングから退出しました。")
    } else {
      player.sendMessage("§cあなたはマッチングに参加していません。")
    }
  }

  private fun startMatchingTimer() {
    val waiting = Config.matchingCompleteAfterWating - Config.matchingCompleteTeleportDuration
    UIHandler.updateGameInfoBar(this)
    matchingTaskId =
      object : BukkitRunnable() {
        var timeElapsed: Int = 0

        override fun run() {
          timeElapsed++
          checkInvalidPlayer()
          if (timeElapsed >= Config.checkDuration && !isCompleted) {
            if (matchingPlayers.size >= min && matchingPlayers.size <= max) {
              isCompleted = true
              sendMessageToMatchingPlayers(Config.messages.matchingCompleteAfterWating)
            } else {
              sendMessageToMatchingPlayers("§e現在 " + matchingPlayers.size + " 人です。引き続き参加者を募集します。")
            }
            timeElapsed = 0
          }
          if (isCompleted) {
            if (timeElapsed >= waiting) {
              sendMessageToMatchingPlayers(
                "§a${matchingPlayers.size}人が集まったので、${Config.matchingCompleteTeleportDuration}秒後に会場に移動します。"
              )
              var i = Config.matchingCompleteTeleportDuration
              object : BukkitRunnable() {
                override fun run() {
                  i--
                  checkInvalidPlayer()
                  if (i < 0) {
                    UIHandler.removeGameInfoBar(matchingPlayers.mapNotNull(UUID::asPlayer))
                    cancel()
                    return
                  } else {
                    UIHandler.updateGameInfoBar(this@MatchingInstance)
                  }
                }
              }.runTaskTimer(plugin, 20L, 20L)
              timeElapsed = -1
              stopMatching()
              startGame()
//              game.createFirstWorld()

//              runTaskLater(Config.matchingCompleteTeleportDuration.toLong()) {
//                stopMatching()
//                return@runTaskLater
//              }
            }
            if (timeElapsed > 0) {
              lastTime =
                Config.matchingCompleteAfterWating.toInt() - timeElapsed - Config.matchingCompleteTeleportDuration
              UIHandler.updateGameInfoBar(this@MatchingInstance)
            }

            if (timeElapsed % 5 == 0 && timeElapsed > 0) {
//              sendMessageToMatchingPlayers("§a残り${lastTime}秒でいどう。")
            }
          }
        }
      }.runTaskTimer(plugin, 0L, 20L).taskId
  }

  fun startGame() = GameManager.createGame(this)

  fun sendMessageToMatchingPlayers(message: String) {
    for (uuid in ArrayList(matchingPlayers)) {
      val p = Bukkit.getPlayer(uuid)
      if (p != null && p.isOnline) {
        p.sendMessage(message)
      }
    }
  }

  fun stopMatching() {
    if (matchingTaskId != -1) {
      Bukkit.getScheduler().cancelTask(matchingTaskId)
      matchingTaskId = -1
    }
  }

  fun reset() {
//    stopMatching()
    runTaskLater(5) { matchingPlayers.clear() }
    plugin.logger.info("Reset matching state.")
  }
}
