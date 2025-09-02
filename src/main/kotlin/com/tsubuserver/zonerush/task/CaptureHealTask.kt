package com.tsubuserver.zonerush.task

import com.tsubuserver.zonerush.BossbarHandler
import com.tsubuserver.zonerush.Config
import com.tsubuserver.zonerush.UIHandler
import com.tsubuserver.zonerush.data.GameTeam
import com.tsubuserver.zonerush.data.SpotData
import com.tsubuserver.zonerush.manager.game.GameInstance
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.sqrt

class CaptureHealTask(
  private val spot: SpotData,
  val executingTeam: GameTeam,
  private val inst: GameInstance,
  private val player: Player
) : BukkitRunnable() {
  override fun run() {
    if (inst.getEnemyPlayersInSpot(spot, executingTeam) > 0) {
      cancelTaskAndReset()
      // TODO: UI表示を「停止中」などに更新
      return
    }

    val friendlyPlayersCount = inst.getFriendlyPlayersInSpot(spot, executingTeam)
    if (friendlyPlayersCount == 0) {
      cancelTaskAndReset()
      return
    }

    val gaugeChangePerSecond = 10.0 * sqrt(friendlyPlayersCount.toDouble())
    when (spot.currentTeam) {
      executingTeam -> {
        val initialGauge = spot.currentGauge
        val expectedIncrease = gaugeChangePerSecond
        val newGauge = (initialGauge + expectedIncrease).coerceAtMost(150.0)
        if (spot.currentGauge < 150) {
          inst.addScore(player.uniqueId) {
            (it ?: 0) + Config.score.afterGetPerSecond
          }
        }
        spot.currentGauge = newGauge
        spot.isDraw = false
        BossbarHandler.updateSpotGauge(spot, color = executingTeam.bossColor)
        if (spot.currentGauge <= 0) {
          inst.broadcastMessage("${executingTeam.deserialize()}が${spot.id}を奪取しました！")
        }
        BossbarHandler.updateSpotGauge(spot, color = spot.currentTeam.bossColor)
      }

      else -> {
        spot.currentGauge -= gaugeChangePerSecond
        if (spot.currentGauge <= 0.0) {
          spot.currentGauge = 30.0
          spot.currentTeam = executingTeam
          spot.isDraw = false
          inst.getFriendlyPlayersInSpotPlayer(spot, executingTeam).forEach {
            inst.addScore(it.uniqueId, 100)
          }
          inst.broadcastMessage("${executingTeam.deserialize()}が${spot.id}を奪取しました！")
          UIHandler.updateRoundInfo(inst)
          spot.centerLocation.world.playSound(spot.centerLocation, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f)
//          cancelTaskAndReset()
//          UIHandler.changeSpotBar(spot, spot.currentTeam.bossColor)
//          plugin.logger.info("Bye: ${spot.currentGauge}")
          // TODO: UIを「陣地を奪取しました」などに更新
        } else {
          spot.isDraw = true
          BossbarHandler.updateSpotGauge(spot, color = GameTeam.NEUTRAL.bossColor)
          // TODO: UIを「奪取中」に更新
        }
      }
    }
    // TODO: リアルタイムでバー表示を更新
  }

  private fun cancelTaskAndReset() {
    spot.currentTask?.cancel()
    spot.currentTask = null
  }
}
