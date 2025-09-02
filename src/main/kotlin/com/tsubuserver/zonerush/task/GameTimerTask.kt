package com.tsubuserver.zonerush.task

import com.tsubuserver.zonerush.manager.game.GameInstance
import org.bukkit.scheduler.BukkitRunnable

class GameTimerTask(
  private val inst: GameInstance
) : BukkitRunnable() {
  override fun run() {
    inst.updateRoundTimer()
  }
}
