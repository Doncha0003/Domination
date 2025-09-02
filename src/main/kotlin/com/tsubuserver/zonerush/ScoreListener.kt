package com.tsubuserver.zonerush

import com.github.peco2282.paperkit.on
import com.tsubuserver.playerDataLib.api.APILib
import com.tsubuserver.zonerush.manager.game.GameManager
import org.bukkit.event.entity.PlayerDeathEvent

object ScoreListener {
  val statsManager by lazy { APILib.statsInstance }

  init {
    val plugin = ZoneRush.getInstance()
    plugin.on<PlayerDeathEvent> {
      val player = it.entity
      val killer = player.killer ?: return@on
      val gameKilled = GameManager.getGameInstance(player.uniqueId) ?: return@on
      val gameKiller = GameManager.getGameInstance(killer.uniqueId) ?: return@on

      if (!gameKilled.players.contains(killer) || !gameKiller.players.contains(player)) return@on
      val game = gameKilled

      game.addScore(player.uniqueId, Config.score.killed)
      game.addScore(killer.uniqueId, Config.score.killer)

      statsManager.editPresent(player.uniqueId) { it ->
        it.deaths++
      }
      statsManager.editPresent(killer.uniqueId) { it ->
        it.kills++
      }
    }
  }
}
