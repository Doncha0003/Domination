package com.tsubuserver.zonerush.manager.game

import com.tsubuserver.zonerush.ZoneRush
import com.tsubuserver.zonerush.manager.matching.MatchingInstance
import com.tsubuserver.zonerush.manager.matching.MatchingManager
import java.util.*

@Suppress("DEPRECATION")
object GameManager {
  enum class GamePhase {
    WAITING_FOR_PLAYERS,
    TELEPORTED_WAITING_PLAYERS,
    TELEPORTED,
    BUY_PHASE,
    ROUND_ACTIVE,
    ROUND_END,
    GAME_END
  }

  private val plugin: ZoneRush = ZoneRush.getInstance()

  val players = mutableMapOf<UUID, GameInstance>()

  fun initialize() {
  }

  fun createGame(matching: MatchingInstance): GameInstance {
    matching.stopMatching()
    val gameInst = GameInstance(matching.gameType, matching)
    gameInst.createFirstWorld()
    for (player in gameInst.players) {
      players[player.uniqueId] = gameInst
    }

    gameInst.startGame()
    return gameInst
  }

  fun stopGame(instance: GameInstance) {
    for (player in instance.players) {
      if (!players.contains(player.uniqueId)) {
        plugin.logger.warning("Player $player is not found in players map.")
      } else {
        players.remove(player.uniqueId)
      }
    }
    MatchingManager.removeMatching(instance.gameType)
  }

  fun shutdown() {
    players.clear()
  }

  fun getGameInstance(playerUUID: UUID): GameInstance? = players[playerUUID]
}
