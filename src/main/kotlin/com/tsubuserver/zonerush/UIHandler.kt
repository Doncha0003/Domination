package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.data.GameTeam
import com.tsubuserver.zonerush.manager.game.GameInstance
import com.tsubuserver.zonerush.manager.matching.MatchingInstance
import fr.mrmicky.fastboard.FastBoard
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.Ticks
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.UUID

object UIHandler {
  private val gameStatusBarUpdater: (GameInstance, FastBoard) -> Unit = { inst, board ->
    val red = inst.redTeamWins
    val blue = inst.blueTeamWins
    val redSpot = inst.controledSpot(GameTeam.RED)
    val blueSpot = inst.controledSpot(GameTeam.BLUE)
    val score = inst.getScore(board.player.uniqueId)
    val list =
      listOf(
        "§f",
        "§d",
        "§c赤チーム: ${red}勝",
        "§c取得陣地: ${redSpot}個",
        "§a",
        "§9青チーム: ${blue}勝",
        "§9取得陣地: ${blueSpot}個",
        "§r",
        "§6スコア: $score"
      )
    board.updateTitle("§b§lZone Rush")
    board.updateLines(list)
    board.updateScores(
      list.map { "" }
    )
  }

  private val gameStatusBarInitializr: (GameInstance, FastBoard) -> Unit = { inst, board ->
    val red = inst.redTeamWins
    val blue = inst.blueTeamWins
    val score = inst.getScore(board.player.uniqueId)
    val list =
      listOf(
        "§f",
        "§d",
        "§c赤チーム: ${red}勝",
        "§c取得陣地: 0個",
        "§a",
        "§9青チーム: ${blue}勝",
        "§9取得陣地: 0個",
        "§r",
        "§6スコア: $score"
      )
    board.updateTitle("§b§lZone Rush")
    board.updateLines(list)
    board.updateScores(
      list.map { "" }
    )
  }
  private val gameStatusBarMap = mutableMapOf<UUID, FastBoard>()

  private val gameInfoBarUpdater: (MatchingInstance, FastBoard) -> Unit = { inst, board ->
    val now = LocalDateTime.now()
    val players = inst.getPlayers()
    val list =
      if (inst.isCompleted) {
        listOf(
          "${now.hour}:${now.minute}:${now.second} (${now.year}/${now.monthValue}/${now.dayOfMonth})",
          "§d",
          "§cプレイヤー数: ${players.size} / ${inst.max}",
          "§c",
          "§a試合開始まであと: ${inst.lastTime}秒",
          "§9",
          "§9ゲーム: Zone Rush",
          "§r"
        )
      } else {
        listOf(
          "${now.hour}:${now.minute}:${now.second} (${now.year}/${now.monthValue}/${now.dayOfMonth})",
          "§d",
          "§cプレイヤー数: ${players.size} / ${inst.max}",
          "§c",
          "§aあと ${inst.min - players.size} 人のプレイヤーが必要です",
          "§9",
          "§9ゲーム: Zone Rush",
          "§r"
        )
      }

    board.updateTitle("§b§lZone Rush")
    board.updateLines(list)
    board.updateScores(
      list.map { "" }
    )
  }

//  private val gameInfoBarInitializr: (MatchingInstance, FastBoard) -> Unit = { inst, board ->}
  private val gameInfoBarMap = mutableMapOf<UUID, FastBoard>()

  fun removeSidebar(players: List<Player>) {
    players.forEach {
      gameStatusBarMap[it.uniqueId]?.let { board ->
        if (!board.isDeleted) {
          board.delete()
          gameStatusBarMap.remove(it.uniqueId)
        }
      }
    }
  }

  fun updateRoundInfo(
    gameInstance: GameInstance,
    initial: Boolean = false
  ) {
    gameInstance.players.forEach {
      val board = gameStatusBarMap[it.uniqueId] ?: FastBoard(it)
      if (initial) gameStatusBarInitializr(gameInstance, board) else gameStatusBarUpdater(gameInstance, board)
      gameStatusBarMap[it.uniqueId] = board
    }
  }

  fun removeGameInfoBar(players: List<Player>) {
    players.forEach {
      gameInfoBarMap[it.uniqueId]?.let { board ->
        if (!board.isDeleted) {
          board.delete()
          gameInfoBarMap.remove(it.uniqueId)
        }
      }
    }
  }

  fun updateGameInfoBar(inst: MatchingInstance) {
    inst.getPlayers().forEach {
      val board = gameInfoBarMap[it] ?: FastBoard(it.asPlayer())
      gameInfoBarUpdater(inst, board)
      gameInfoBarMap[it] = board
    }
  }

  fun sendTitle(
    players: List<Player>,
    title: String,
    subTitle: String,
    fadeIn: Int = 5,
    stay: Int = 30,
    fadeOut: Int = 5
  ) {
    val titleInstance =
      Title.title(
        title.toComponent(),
        subTitle.toComponent(),
        Title.Times.times(
          Ticks.duration(fadeIn.toLong()),
          Ticks.duration(stay.toLong()),
          Ticks.duration(fadeOut.toLong())
        )
      )
    players.forEach { it.showTitle(titleInstance) }
  }
}
