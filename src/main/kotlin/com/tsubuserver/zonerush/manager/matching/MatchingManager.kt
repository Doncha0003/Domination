package com.tsubuserver.zonerush.manager.matching

import com.tsubuserver.zonerush.Command
import com.tsubuserver.zonerush.UIHandler
import com.tsubuserver.zonerush.ZoneRush
import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.mutableEnumMap
import org.bukkit.boss.KeyedBossBar
import org.bukkit.entity.Player
import java.util.UUID

object MatchingManager {
  private val instances =
    mutableEnumMap<GameType, MatchingInstance?> { null }

  fun getMatchingInstance(player: UUID): MatchingInstance? {
    instances.entries.forEach { (_, inst) ->
      if (inst != null && inst.getPlayers().contains(player)) {
        return inst
      }
    }
    return null
  }

  fun createMatching(
    player: Player,
    gameType: GameType
  ) {
//    val worldName = Utils.generatePlayWorld(gameType)
    val inst = instances[gameType] ?: MatchingInstance(gameType)
    inst.addPlayerToMatching(player)
    instances[gameType] = inst

    if (inst.getPlayers().size >= gameType.max) {
      stopMatching(inst)
    }
  }

  fun stopMatching(instance: MatchingInstance) {
    instance.startGame()
    instance.stopMatching()
    instances[instance.gameType]?.stopMatching()
    instances[instance.gameType] = null
  }

  fun stopMatching(player: Player): Int {
    var instance: MatchingInstance? = null
    instances.entries.forEach { (_, inst) ->
      if (inst != null && inst.getPlayers().contains(player.uniqueId)) {
        instance = inst
      }
    }
    if (instance == null) {
      player.sendMessage("あなたはマッチングに参加していません")
      return Command.FAILED
    }
    player.activeBossBars().forEach {
      // remove zonerush's boss-bar
      if (it is KeyedBossBar && it.key.namespace == ZoneRush.getInstance().name) {
        player.hideBossBar(it)
        it.removePlayer(player)
      }
    }
    UIHandler.removeGameInfoBar(listOf(player)) // remove side-bar
    instance.detachPlayerMatching(player)
    return Command.SUCCESS
  }

  fun removeMatching(gameType: GameType) {
    instances[gameType]?.stopMatching()
    instances[gameType] = null
  }
}
