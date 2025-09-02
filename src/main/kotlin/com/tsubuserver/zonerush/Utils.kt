package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.manager.game.GameInstance.Companion.plugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.apache.commons.io.FileUtils
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import kotlin.random.Random

fun String.toComponent(): Component = LegacyComponentSerializer.legacySection().deserialize(this)

inline fun <reified E : Enum<E>, V> mutableEnumMap(defaultValue: () -> V) =
  mutableMapOf<E, V>().apply {
    enumValues<E>().associateWithTo(this) { defaultValue() }
  }

fun runTaskLater(
  delay: Long,
  task: () -> Unit
) = ZoneRush
  .getInstance()
  .server.scheduler
  .runTaskLater(ZoneRush.getInstance(), task, delay * 20)

fun runTaskTimer(
  delay: Long,
  period: Long,
  task: () -> Unit
) = ZoneRush
  .getInstance()
  .server.scheduler
  .runTaskTimer(ZoneRush.getInstance(), task, delay * 20, period * 20)

fun UUID.asPlayer() = Bukkit.getPlayer(this)

fun Location.teleport(players: Collection<Player>) =
  players.forEach {
    it.teleport(this)
  }

object Utils {
  fun generatePlayWorld(type: GameType): Config.WorldType {
    val allWorlds = Config.getBattleWorlds().filter { it.value.type == type }

    return allWorlds[allWorlds.keys.random()]!!.also {
      ZoneRush.getInstance().logger.info("World: ${it.name} (${it.type})")
    }
  }

  fun <T> splitRandomly(array: List<T>): Pair<List<T>, List<T>> {
    val shuffled =
      array
        .toMutableList()
        .apply {
          shuffle(Random)
          shuffle()
        }
    val mid = shuffled.size / 2
    val firstHalf = shuffled.take(mid)
    val secondHalf = shuffled.drop(mid)
    return firstHalf to secondHalf
  }

  fun createWorld(
    name: String,
    dest: String
  ): World? {
    val templatePath = File(plugin.dataFolder, "template/$name")
    val worldPath = File(Bukkit.getWorldContainer(), dest)
    try {
      FileUtils.copyDirectory(templatePath, worldPath)
      File(worldPath, "uid.dat").delete()
      val world = Bukkit.createWorld(WorldCreator(dest))
      world?.apply {
        setGameRule(GameRule.KEEP_INVENTORY, true)
        setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        setGameRule(GameRule.DO_MOB_SPAWNING, false)
        setGameRule(GameRule.FALL_DAMAGE, false)
        setGameRule(GameRule.SHOW_DEATH_MESSAGES, false)
        setGameRule(GameRule.NATURAL_REGENERATION, false)

        difficulty = Difficulty.PEACEFUL
      }

      return world
    } catch (e: Exception) {
      plugin.logger.warning("Failed to copy template world: $name")
      e.printStackTrace()
    }
    return null
  }

  fun deleteWorld(name: String): Boolean {
    val worldPath = File(Bukkit.getWorldContainer(), name)
    try {
      Bukkit.unloadWorld(name, false)
      runTaskLater(1) {
        FileUtils.deleteDirectory(worldPath)
      }
      return true
    } catch (e: Exception) {
      plugin.logger.warning("Failed to delete world: $name")
      e.printStackTrace()
    }
    return false
  }
}
