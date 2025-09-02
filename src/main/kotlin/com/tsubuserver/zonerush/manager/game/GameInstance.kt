package com.tsubuserver.zonerush.manager.game

import com.github.peco2282.paperkit.component
import com.tsubuserver.playerDataLib.api.APILib
import com.tsubuserver.zonerush.*
import com.tsubuserver.zonerush.data.GameTeam
import com.tsubuserver.zonerush.data.GameTeam.*
import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.data.SpotData
import com.tsubuserver.zonerush.manager.game.GameManager.GamePhase
import com.tsubuserver.zonerush.manager.matching.MatchingInstance
import com.tsubuserver.zonerush.task.GameTimerTask
import com.tsubuserver.zonerush.task.SpotChangeTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*


class GameInstance(
  val gameType: GameType,
  val matching: MatchingInstance,
) {
  private val teams = mutableMapOf<UUID, GameTeam>()
  private val spotChangeTask: SpotChangeTask = SpotChangeTask(this)
  val players = matching.getPlayers().mapNotNull { Bukkit.getPlayer(it) }
  private val healedPlayers: MutableMap<UUID, Int> = mutableMapOf()
  private val stages: List<Config.WorldType>

  var worldType: Config.WorldType

  init {
    matching.reset()

    this.stages = List(5) {
      val w = Utils.generatePlayWorld(gameType);
      w.copy(suffix = randomSuffix())
    }
    this.worldType = stages[0]
    this.spotChangeTask.worldType = this.worldType
  }

  var currentRound: Int = 0
  var redTeamWins: Int = 0
  var blueTeamWins: Int = 0
  var roundTimeLeft: Int = 0
  private var gameTimerTask: BukkitRunnable? = null
  private var lastWorld = null as World?
  private var currWorld = null as World?

  private val immunePlayers = mutableListOf<UUID>()

  /**
   * プレイヤーを無敵リストに追加する
   * @param uuid プレイヤーのUUID
   */
  fun addImmunePlayer(uuid: UUID) {
    immunePlayers.add(uuid)
  }

  /**
   * プレイヤーを無敵リストから削除する
   * @param uuid プレイヤーのUUID
   */
  fun removeImmunePlayer(uuid: UUID?) {
    immunePlayers.remove(uuid)
  }

  /**
   * プレイヤーが無敵状態かチェックする
   * @param uuid プレイヤーのUUID
   * @return 無敵状態であればtrue
   */
  fun isImmune(uuid: UUID?): Boolean {
    return immunePlayers.contains(uuid)
  }

  fun addScore(uuid: UUID, score: (Int?) -> Int) {
    val newScore = score(getScore(uuid))
//    healedPlayers.compute(uuid) { _, current ->
//      uuid.asPlayer()?.sendMessage("> ${newScore - getScore(uuid)} ポイント追加 $newScore")
//      newScore}

    UIHandler.updateRoundInfo(this)
  }

  fun addScore(uuid: UUID, score: Int) {
//    healedPlayers.compute(uuid) { _, current ->
//      uuid.asPlayer()?.sendMessage("> ${score}ポイント追加 ${(current ?: 0) + score}")
//      (current ?: 0) + score}

    UIHandler.updateRoundInfo(this)
  }


  fun getScore(uuid: UUID) = healedPlayers.getOrPut(uuid) { 0 }

  val prices = mutableMapOf<UUID, Int>()

  companion object {
    val plugin = ZoneRush.getInstance()

    private val CHARS = "abcdefghijklmnopqrstuvwxyz".uppercase() + "0123456789"
    private val random = Random()
    private val randomSuffix: () -> String = {
      val sb = StringBuilder()
      repeat(5) {
        sb.append(CHARS[random.nextInt(CHARS.length)])
      }
      sb.toString()
    }
  }

  private fun setupPlayerTeamDisplay(){ //試合開始時プレイヤーのディスプレイネームにチームの色を反映
    players.forEach { player ->
      val team = teams[player.uniqueId] ?: return@forEach
      val teamPrefix = when(team) {
        RED -> "§c"
        BLUE -> "§9"
        NEUTRAL -> "§7"
      }
//      player.displayName(Component.text("${teamPrefix}${player.name}"))
      player.displayName(teamPrefix.component().append(player.name.component()))
    }
  }
  private fun resetPlayerTeamDisplay() { //試合終了時プレイヤーのディスプレイネームを初期状態に戻す
    players.forEach { player ->
      player.displayName(Component.text(player.name))
    }
  }

  fun getTeam(playerUUID: UUID): GameTeam? = teams[playerUUID]

  var gamePhase: GamePhase = GamePhase.WAITING_FOR_PLAYERS

  fun setup() {
    BossbarHandler.setup(worldType)
    val (redTeamPlayers, blueTeamPlayers) = Utils.splitRandomly(players.shuffled())

    assignTeam(redTeamPlayers, RED, worldType.defaultRedLocation)
    assignTeam(blueTeamPlayers, BLUE, worldType.defaultBlueLocation)
    setupPlayerTeamDisplay()
  }

  private fun assignTeam(teamPlayers: List<Player>, team: GameTeam, spawnLocation: Location) {
    teamPlayers.forEach {
      teams[it.uniqueId] = team
      teleportPlayersToArena(it, spawnLocation)
      it.sendMessage(team.colored())
      UIHandler.updateRoundInfo(this, true)
      prices[it.uniqueId] = Config.initialEquipment!!.base
      it.sendMessage(
        Component.text("現在は、", NamedTextColor.WHITE)
          .append(Component.text(Config.initialEquipment!!.base, NamedTextColor.YELLOW))
          .append("ポイント所持しています".toComponent().color(NamedTextColor.WHITE))
      )
      it.getAttribute(Attribute.KNOCKBACK_RESISTANCE)!!.baseValue = 1.0;

      it.inventory.clear()
    }
  }

  fun getEnemyPlayersInSpot(spot: SpotData, playerTeam: GameTeam) =
    players.count { teams[it.uniqueId] != playerTeam && spot.isInside(it.location) }

  fun getFriendlyPlayersInSpot(spot: SpotData, playerTeam: GameTeam) =
    players.count { teams[it.uniqueId] == playerTeam && spot.isInside(it.location) }

  fun getFriendlyPlayersInSpotPlayer(spot: SpotData, playerTeam: GameTeam) =
    players.filter { teams[it.uniqueId] == playerTeam && spot.isInside(it.location) }

  fun purchasePhase() {
    BossbarHandler.onPurchasePhaseStart(players, worldType)
    gamePhase = GamePhase.BUY_PHASE
//    UIHandler.sendTitle(players, "§e武器購入フェーズ！", "")
    // TODO: お金管理
    players
      .forEach(GuiHandler::openShop)
    players.forEach {
      it.sendMessage(
        Component
          .text("ショップを開く")
          .color(NamedTextColor.YELLOW)
          .style { builder ->
            builder.decorate(TextDecoration.BOLD)
            builder.clickEvent(
              ClickEvent.clickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/zr s"
              )
            )
          }
      )

      object : BukkitRunnable() {
        var ticksElapsed = 0
        override fun run() {
          if (it.isOnline && ticksElapsed < 20) { // 400tick = 20秒
            it.sendActionBar(
              Component
                .text("インベントリを開いてアイテムを購入しよう")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
            )
            ticksElapsed++
          } else {
            // 20秒経過またはプレイヤーがオフラインになったらタスクを停止
            if (it.isOnline) {
              it.sendActionBar(Component.empty())
            }
            cancel()
          }
        }
      }.runTaskTimer(plugin, 0L, 20L)
    }
  }

  // TODO: ラウンド管理、タイマー、勝利条件判定
  fun startNewRound() {
    currentRound++
    if (currentRound > worldType.spots.size || redTeamWins >= 3 || blueTeamWins >= 3) {
      endGame()
      return
    }
    BossbarHandler.setup(worldType)
    plugin.logger.info("Start new round: $currentRound")

    worldType.spots.forEach {
      it.currentTeam = NEUTRAL
      it.currentGauge = 50.0
      it.currentTask?.cancel()
      it.currentTask = null
      // TODO: 陣地のパーティクル色もリセット
    }

    worldType.fill(currWorld!!)
//    worldType.fillBarrier(currWorld!!)

    players.forEach { it.inventory.clear() }

    teams
      .forEach { p, team ->
        p.asPlayer()?:return@forEach
        team
          .initialEquip(p.asPlayer()!!)
        val player = p.asPlayer()!!
        player.health = player.getAttribute(Attribute.MAX_HEALTH)!!.value
        player.foodLevel = 20
      }

    if (lastWorld != null) {
      runTaskLater(5) {
        Utils.deleteWorld(lastWorld!!.name)
      }
    }

    gamePhase = GamePhase.TELEPORTED

    runTaskLater(Config.roundTitleDisplayDuration.toLong()) {
      players.forEach {
        it.gameMode = GameMode.ADVENTURE
        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
      }
      runTaskLater(1L) {
        purchasePhase()
      }
      UIHandler.sendTitle(
        players,
        "§b§lラウンド $currentRound 開始！",
        "§e武器購入フェーズ！",
        stay = Config.roundTitleDisplayDuration * 20
      )

      var i = Config.buyPhaseDuration
      BossbarHandler.onPurchasePhaseStart(players, worldType)
      val task = runTaskTimer(1L, 1L) {
        BossbarHandler.updatePurchasingTimer(i, worldType)
        i--
      }

      runTaskLater(Config.buyPhaseDuration.toLong() + 1) {
        task.cancel()
        players.forEach {
          it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f)
        }
        BossbarHandler.onPurchasePhaseStop(worldType)
        BossbarHandler.onGameStart(players, worldType)
        gamePhase = GamePhase.ROUND_ACTIVE
        UIHandler.sendTitle(players, "§a§l戦闘開始！", "")
        worldType.remove(currWorld!!)
//        worldType.breakBarrier(currWorld!!)
        roundTimeLeft = Config.roundTimeoutDuration
        gameTimerTask?.cancel()
        gameTimerTask = GameTimerTask(this)
        gameTimerTask?.runTaskTimer(plugin, 0L, 20L)
        UIHandler.updateRoundInfo(this, true)
      }
    }
  }

  fun updateRoundTimer() {
    val spotSize = worldType.spots.size
    if (roundTimeLeft > 0) {
      roundTimeLeft--
      BossbarHandler.updatePlayingTimer(roundTimeLeft, worldType, players)


      if (roundTimeLeft <= 10 && roundTimeLeft > 0) {
        UIHandler.sendTitle(
          players,
          "残り §c${roundTimeLeft} 秒",
          "",
          5, 20, 5
        )
        players.forEach {
          it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
        }
      }
    } else {
      gameTimerTask?.cancel()
      endRoundByTime()
    }

    val redControlledSpots = controledSpot(RED)
    val blueControlledSpots = controledSpot(BLUE)

    if (redControlledSpots == spotSize) {
      startImmediateWinCountdown(RED)
    } else if (blueControlledSpots == spotSize) {
      startImmediateWinCountdown(BLUE)
    } else {
      if (immediateWinCountdownTask != null) {
        immediateWinCountdownTask?.cancel()
        immediateWinCountdownTask = null
        broadcastMessage("§e全陣地制圧カウントダウンが解除されました。")
      }
    }
  }

  fun controledSpot(team: GameTeam): Int = worldType.spots.count { it.currentTeam == team && it.currentGauge >= 0 }

  private var immediateWinCountdownTask: BukkitRunnable? = null
  private var immediateWinTeam: GameTeam? = null
  private var immediateWinTimeLeft: Int = 15

  /* 全陣地を取得した際のアクション */
  fun startImmediateWinCountdown(winningTeam: GameTeam) {
    if (immediateWinCountdownTask == null || immediateWinTeam != winningTeam) {
      if (roundTimeLeft < 16) return // 0 ~ 15以内ではカウントダウン始まらない
      immediateWinTimeLeft = 15 // 残り15秒で設定
      immediateWinTeam = winningTeam // 現在の勝利判定を受けているチーム(最終決定はしていない)
      immediateWinCountdownTask?.cancel() // 過去のｲﾍﾞﾝﾄをキャンセル(念のため)

      immediateWinCountdownTask = object : BukkitRunnable() { // 15秒カウントダウンタイマー
        override fun run() {
          UIHandler.sendTitle(
            players,
            "§l${winningTeam.deserialize()}§r§lが全陣地を制圧中！",
            "残り ${immediateWinTimeLeft}秒で終了！"
          )
          players.forEach {
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
          }
          immediateWinTimeLeft-- // 15秒から毎秒減っていく

          if (immediateWinTimeLeft < 0) {
            immediateWinCountdownTask?.cancel()
            immediateWinCountdownTask = null
            UIHandler.sendTitle(players, "§l${winningTeam.deserialize()}§r§lが全陣地を維持し、ラウンド勝利！", "")
            players.forEach {
              it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
            }
            endRound(winningTeam)
          }
        }
      }
      immediateWinCountdownTask?.runTaskTimer(plugin, 0, 20)
    }
  }

  private fun endRoundByTime() {
    gamePhase = GamePhase.ROUND_END
    val redSpots = worldType.spots.count { it.currentTeam == RED }
    val blueSpots = worldType.spots.count { it.currentTeam == BLUE }

    val winningTeam: GameTeam = when {
      redSpots > blueSpots -> RED
      blueSpots > redSpots -> BLUE
      else -> { // 同数の場合、ゲージ合計で判定
        val redGaugeTotal = worldType.spots.filter { it.currentTeam == RED }.sumOf { it.currentGauge }
        val blueGaugeTotal = worldType.spots.filter { it.currentTeam == BLUE }.sumOf { it.currentGauge }
        when {
          redGaugeTotal > blueGaugeTotal -> RED
          blueGaugeTotal > redGaugeTotal -> BLUE
          else -> listOf(RED, BLUE).random()
        }
      }
    }

    endRound(winningTeam)
  }

  fun endRound(winner: GameTeam) {
    BossbarHandler.stop(worldType)
    gamePhase = GamePhase.ROUND_END
    gameTimerTask?.cancel()
    worldType.spots.forEach { it.currentTask?.cancel() }
    worldType.fill(currWorld!!)
//    worldType.fillBarrier(currWorld!!)

    fun givingPrice(winnerPrice: Int, loserPrice: Int, player: UUID): Int {
      val p = player.asPlayer() ?: return 0
      val team = teams[player]?:return 0
      return (if (team == winner) winnerPrice else loserPrice)
        .apply {
          val v = this
          p.
            sendMessage(
              Component.text(v, NamedTextColor.GREEN).append("ポイント入手しました".toComponent())
            )
        }
    }

    val initialEquipment = Config.initialEquipment!!

    players.forEach {
      if (getTeam(it.uniqueId) == winner) {
        addScore(it.uniqueId, Config.score.winnerOfRound)
      } else {
        addScore(it.uniqueId, Config.score.loserOfRound)
      }
      it.inventory.clear()
      it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f)
      when (currentRound) {
        1 ->
          prices.compute(it.uniqueId) { key, current ->
            if (current == null) return@compute 0
            current + givingPrice(initialEquipment.round1Winner, initialEquipment.round1Loser, key)
          }

        2 ->
          prices.compute(it.uniqueId) { key, current ->
            if (current == null) return@compute 0
            current + givingPrice(initialEquipment.round2Winner, initialEquipment.round2Loser, key)
          }

        3 ->
          prices.compute(it.uniqueId) { key, current ->
            if (current == null) return@compute 0
            current + givingPrice(initialEquipment.round3Winner, initialEquipment.round3Loser, key)
          }

        4 ->
          prices.compute(it.uniqueId) { key, current ->
            if (current == null) return@compute 0
            current + givingPrice(initialEquipment.round4Winner, initialEquipment.round4Loser, key)
          }
      }
      it.sendMessage(
        Component.text("現在は、", NamedTextColor.WHITE)
          .append(Component.text(prices[it.uniqueId]!!, NamedTextColor.YELLOW))
          .append("ポイント所持しています".toComponent().color(NamedTextColor.WHITE))
      )
    }

    when (winner) {
      RED -> {
        redTeamWins++
        UIHandler.sendTitle(players, "§6 ラウンド終了！", "§c赤チーム§lの勝利！")
      }

      BLUE -> {
        blueTeamWins++
        UIHandler.sendTitle(players, "§6 ラウンド終了！", "§9青チーム§lの勝利！")
      }

      else -> {
        UIHandler.sendTitle(
          players,
          "§6 ラウンド終了！",
          "§7引き分け（ランダム判定）により、${winner.deserialize()}の勝利！"
        ) // ランダムで勝者が決まるため、基本的には発生しないが念のため
      }
    }


    teams.forEach { (uuid, team) ->
      val p = uuid.asPlayer() ?: return@forEach
      UIHandler.updateRoundInfo(this, true)
      when (team) {
        RED -> teleportPlayersToArena(p, worldType.defaultRedLocation)
        BLUE -> teleportPlayersToArena(p, worldType.defaultBlueLocation)
        NEUTRAL -> teleportPlayersToArena(p, worldType.defaultBlueLocation)
      }
    }
    val validPlayers = players.filter { it.isOnline && it.uniqueId.asPlayer() != null }
    if (redTeamWins >= 3 || blueTeamWins >= 3 || currentRound >= 5 || (validPlayers.size < gameType.min || validPlayers.size > gameType.max)) {
      endGame()
    } else {
      val oldWorld = worldType.worldName
      worldType = stages[currentRound]
      lastWorld = currWorld
      currWorld = Utils.createWorld(worldType.name, worldType.worldName) ?: return
      object : BukkitRunnable() {
        var time: Int = Config.countdownDuration
        override fun run() {
          if (time <= 0) {
            spotChangeTask.worldType = worldType
//            worldType.fill(currWorld!!)
            teams.filter { it.key.asPlayer() != null }.forEach { (uuid, team) ->
              if (team == RED) {
                teleportPlayersToArena(uuid.asPlayer()!!, worldType.defaultRedLocation)
              } else if (team == BLUE) {
                teleportPlayersToArena(uuid.asPlayer()!!, worldType.defaultBlueLocation)
              }
            }
            startNewRound()
            cancel()
            runTaskLater(
              5
            ) {
              Utils.deleteWorld(oldWorld)
            }
            return
          } else {
            UIHandler
              .sendTitle(players, "開始まで $time 秒", "", 2, 10, 2)
          }
          time--
        }
      }.runTaskTimer(plugin, 20L, 20L)
    }
//    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
//      if (redTeamWins >= 3 || blueTeamWins >= 3 || currentRound >= 5) {
//        endGame()
//      } else {
//        startNewRound()
//      }
//    }, 20 * 5L)
  }

  fun endGame(stoppedByOwner: Boolean = false) {
    if (stoppedByOwner) {
      UIHandler.sendTitle(players, "管理者によって強制終了されました", "", 20, 40, 20)
    }
    resetPlayerTeamDisplay()
    gamePhase = GamePhase.GAME_END
    spotChangeTask.cancel()
    broadcastMessage("§d§l--- ゲーム終了！ ---")
    val finalWinner: GameTeam? = when {
      redTeamWins > blueTeamWins -> RED
      blueTeamWins > redTeamWins -> BLUE
      else -> null // 同点の場合
    }

    teams.forEach { (uuid, team) ->
      uuid.asPlayer() ?: return@forEach
      if (team == finalWinner) addScore(uuid, Config.score.winnerOfGame) else addScore(uuid, Config.score.loserOfGame)
    }


    val (member, score) = healedPlayers.filter { teams[it.key] == finalWinner }.maxByOrNull { it.value }?.let { it.key to it.value }
      ?: (null to -1)
    if (member?.asPlayer() != null && score > 0.0) {
      broadcastMessage("MVP: §e${member.asPlayer()!!.name}§r§7 (§a+$score§7)")
    } else if (member?.asPlayer() == null) {
      broadcastMessage("MVP: §c§l該当プレイヤーが見つかりませんでした")
    }
    UIHandler.updateRoundInfo(this)
    sendScore2DB()

    players.forEach {
      it.getAttribute(Attribute.KNOCKBACK_RESISTANCE)!!.baseValue = .0;
      it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
    }

    if (finalWinner != null) {
      broadcastMessage("§b最終的な勝者は §l${finalWinner.deserialize()}§bだ！")
      UIHandler.sendTitle(
        players,
        "§b§l最終的な勝者は §l${finalWinner.deserialize()}§bだ！",
        "§75秒後にロビーに戻ります...",
        stay = Config.roundTitleDisplayDuration * 20
      )
    } else {
      broadcastMessage("§7ゲームは引き分けでした！")
    }

    BossbarHandler.stop(worldType)
    if (stoppedByOwner) {
      gameTimerTask?.cancel()
      immediateWinCountdownTask?.cancel()
      spotChangeTask.cancel()
    }
    broadcastMessage("§75秒後にロビーに戻ります...")
    // TODO: ゲーム終了時の報酬配布やロビーへの移動など
    runTaskLater(5) {
      players.forEach {
        it.inventory.clear()
      }
      UIHandler.removeSidebar(players)

      Config.lobbyLocation!!.clone().teleport(players)
      runTaskLater(5) {
        currWorld?.name.also { it?.let { Utils.deleteWorld(it) } } ?: plugin.logger.warning("Failed to delete world!")
      }
//      val def = "${defLoc.world.spawnLocation.x},${defLoc.world.spawnLocation.y},${defLoc.world.spawnLocation.z}"
//      coreApi.destinationsProvider
//        .parseDestination("e:${defLoc.world.name}:$def")
//        .peek {
//          coreApi.safetyTeleporter
//            .to(it)
//            .checkSafety(true)
//            .teleport(players)
//        }.onFailure { _ ->
//          plugin.logger.warning("Failed to parse destination!")
//        }
    }

    GameManager.stopGame(this)
    teams.clear()
    prices.clear()
    healedPlayers.clear()
  }

  private fun sendScore2DB() {
    val statsManager = APILib.statsInstance
    for (player in healedPlayers) {
      val uuid = player.key
      val score = player.value.toInt()
      statsManager.edit(uuid) {
        it.score += score
        it
      }
    }
  }

  private fun teleportPlayersToArena(player: Player, dest: Location) {
    val loc = Location(currWorld, dest.x, dest.y, dest.z)
    player.teleport(loc)
  }

  fun createFirstWorld() {
    currWorld = Utils.createWorld(worldType.name, worldType.worldName)
      ?: let { plugin.logger.warning("Failed to create world!"); return }
    worldType.fill(currWorld!!)
  }

  fun startGame() {
    if (currentRound != 0)
      currWorld = Utils.createWorld(worldType.name, worldType.worldName)
        ?: let { plugin.logger.warning("Failed to create world!"); return }
    runTaskLater(Config.matchingCompleteTeleportDuration.toLong()) {
      setup()
      runTaskLater(Config.roundStartTeleportDelay.toLong()) {
//    purchasePhase()
        startNewRound()
        spotChangeTask.runTaskTimer(plugin, 20L, 20L)
      }
    }
  }

  fun broadcastMessage(message: String) {
    for (player in players) {
      player.sendMessage(message)
    }
  }
}
