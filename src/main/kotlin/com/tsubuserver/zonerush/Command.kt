package com.tsubuserver.zonerush

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.manager.game.GameManager
import com.tsubuserver.zonerush.manager.matching.MatchingManager
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player

@Suppress("UnstableApiUsage")
object Command {
  const val SUCCESS = 1
  const val FAILED = -1

  fun create(): LiteralCommandNode<CommandSourceStack> {
    val builder = literal("zonerush")
    builder
      .then(
        literal("reload")
          .requires { it.sender.isOp }
          .executes {
            Config.load(ZoneRush.getInstance().config)
            it.source.sender.sendMessage("コンフィグをリロードしました".toComponent().color(NamedTextColor.GREEN))
            GuiHandler.load(true)
            it.source.sender.sendMessage("ショップ内容をリロードしました".toComponent().color(NamedTextColor.GREEN))
            SUCCESS
          }
      ).then(
        literal("matching")
          .then(
            literal("start")
              .then(
                argument("game_type", StringArgumentType.string())
                  .suggests { _, builder ->
                    GameType.entries.forEach {
                      if (it != GameType.UNDEFINED) {
                        builder.suggest(it.type)
                      }
                    }
                    builder.buildFuture()
                  }.executes {
                    if (it.source.sender !is Player) {
                      return@executes FAILED
                    }
                    val gameType = GameType.fromString(StringArgumentType.getString(it, "game_type"))
                    MatchingManager.createMatching(it.source.sender as Player, gameType)
                    SUCCESS
                  }
              )
          ).then(
            literal("cancel")
              .executes {
                if (it.source.sender !is Player) {
                  return@executes FAILED
                }
                MatchingManager.stopMatching(it.source.sender as Player)
              }
          ).then(
            literal("stop")
              .requires { it.sender.isOp }
              .then(
                argument("player", ArgumentTypes.player())
                  .executes {
                    it.source.sender.sendMessage("これは実験的機能です".toComponent().color(NamedTextColor.YELLOW))
                    val player =
                      it
                        .getArgument(
                          "player",
                          PlayerSelectorArgumentResolver::class.java
                        ).resolve(it.source)
                        .firstOrNull()
                    if (player == null) {
                      it.source.sender.sendMessage(
                        "プレイヤーが見つかりません".toComponent().color(NamedTextColor.YELLOW)
                      )
                      return@executes FAILED
                    }

                    val inst = GameManager.getGameInstance(player.uniqueId)
                    if (inst == null) {
                      it.source.sender.sendMessage(
                        "ゲームが見つかりません".toComponent().color(NamedTextColor.DARK_RED)
                      )
                      return@executes FAILED
                    }

                    inst.endGame(true)
                    it.source.sender.sendMessage("ゲームを終了しました".toComponent().color(NamedTextColor.DARK_RED))

                    SUCCESS
                  }
              )
          )
//          .executes {
//            MatchingManager.createMatching(it.source.sender as Player, GameType.VS_1)
//            1
//          }
      ).then(
        literal("worlds")
          .requires { it.sender.hasPermission("op") }
          .executes {
            it.source.sender.sendMessage("Worlds: ${Config.getBattleWorlds().keys}")
            it.source.sender.sendMessage(*Bukkit.getWorlds().map { it.name }.toTypedArray())
            SUCCESS
          }
      ).then(
        literal("shop")
          .executes {
            GuiHandler.openShop(it.source.sender as Player)
            1
          }
      ).then(
        literal("create")
          .then(
            argument("world", StringArgumentType.string())
              .requires { it.sender.isOp }
              .suggests { _, builder ->
                Config
                  .getBattleWorlds()
                  .keys
                  .forEach { builder.suggest(it) }
                builder.buildFuture()
              }.executes {
                if (it.source.sender !is Player) {
                  return@executes FAILED
                }
                val worldName = StringArgumentType.getString(it, "world")
                val result = Utils.createWorld(worldName, worldName)
                if (result == null) {
                  it.source.sender.sendMessage("世界の作成に失敗しました。".toComponent().color(NamedTextColor.RED))
                } else {
                  it.source.sender.sendMessage("世界が作成されました。".toComponent().color(NamedTextColor.GREEN))
                }
                SUCCESS
              }
          )
      ).then(
        literal("delete")
          .then(
            argument("world", StringArgumentType.string())
              .requires { it.sender.isOp }
              .suggests { _, builder ->
                Config
                  .getBattleWorlds()
                  .keys
                  .forEach { builder.suggest(it) }
                builder.buildFuture()
              }.executes {
                if (it.source.sender !is Player) {
                  return@executes FAILED
                }
                val worldName = StringArgumentType.getString(it, "world")
                val result = Utils.deleteWorld(worldName)
                if (!result) {
                  it.source.sender.sendMessage("世界の削除に失敗しました。".toComponent().color(NamedTextColor.RED))
                } else {
                  it.source.sender.sendMessage("世界が削除されました。".toComponent().color(NamedTextColor.GREEN))
                }
                SUCCESS
              }
          )
      )

    return builder.build()
  }

  fun createShorter(): LiteralCommandNode<CommandSourceStack> {
    val builder = literal("zr")
    builder
      .then(
        literal("m")
          .then(
            literal("s")
              .then(
                argument("game_type", StringArgumentType.string())
                  .suggests { _, builder ->
                    GameType.entries.forEach {
                      if (it != GameType.UNDEFINED) {
                        builder.suggest(it.type)
                      }
                    }
                    builder.buildFuture()
                  }.executes {
                    if (it.source.sender !is Player) {
                      return@executes FAILED
                    }
                    val gameType = GameType.fromString(StringArgumentType.getString(it, "game_type"))
                    MatchingManager.createMatching(it.source.sender as Player, gameType)
                    SUCCESS
                  }
              )
          ).then(
            literal("c")
              .executes {
                if (it.source.sender !is Player) {
                  return@executes FAILED
                }
                MatchingManager.stopMatching(it.source.sender as Player)
              }
          )
      ).then(
        literal("s").executes {
          GuiHandler.openShop(it.source.sender as Player)
          SUCCESS
        }
      )

    return builder.build()
  }
}
