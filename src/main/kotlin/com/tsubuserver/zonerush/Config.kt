package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.data.GameTeam
import com.tsubuserver.zonerush.data.GameType
import com.tsubuserver.zonerush.data.SpotData
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerializable

object Config {
  data class ShopItem(
    val name: String,
    val price: Int,
    val lore: List<String>
  )

  data class Area(
    val fill: Location,
    val remove: Location
  ) {
    fun putBlock(
      world: World,
      location: Location
    ) {
      world.getBlockAt(location).type = Material.REDSTONE_BLOCK
      runTaskLater(1) { world.getBlockAt(location).type = Material.AIR }
    }

    fun fill(world: World) = putBlock(world, fill)

    fun remove(world: World) = putBlock(world, remove)
  }

  data class WorldType(
    val name: String,
    val type: GameType,
    val defaultBlueLocation: Location,
    val defaultRedLocation: Location,
    val defaultLocation: Location,
    val spots: List<SpotData>,
    val area: Area,
    val suffix: String = ""
  ) {
    val worldName = "${name}_$suffix"

    fun fill(world: World) = area.fill(world)

    fun remove(world: World) = area.remove(world)
  }

  data class PriceData(
    val base: Int,
    val round1Winner: Int,
    val round1Loser: Int,
    val round2Winner: Int,
    val round2Loser: Int,
    val round3Winner: Int,
    val round3Loser: Int,
    val round4Winner: Int,
    val round4Loser: Int
  )

  data class Messages(
    val matchingCompleteAfterWating: String
  )

  data class Score(
    val winnerOfRound: Int,
    val loserOfRound: Int,
    val winnerOfGame: Int,
    val loserOfGame: Int,
    val getSpot: Int,
    val afterGetPerSecond: Int,
    val killed: Int,
    val killer: Int
  )

  private val battleWorlds = mutableMapOf<String, WorldType>()

  private val plugin = ZoneRush.getInstance()
  var systemConfig: SystemConfig? = null
  var range: Double = 2.0
  var checkDuration: Double = 1.0

  private var _messages: Messages? = null
  private var _score: Score? = null

  val messages: Messages
    get() {
      return _messages!!
    }

  val score: Score
    get() {
      return _score!!
    }

  var matchingCompleteAfterWating: Double = 0.0
  var matchingCompleteTeleportDuration: Int = 0
  var roundStartTeleportDelay: Int = 0
  var roundTitleDisplayDuration: Int = 0
  var buyPhaseDuration: Int = 0
  var countdownDuration: Int = 0
  var roundTimeoutDuration: Int = 0
  var interRoundDelay: Int = 0

  var lobbyLocation: Location? = null

  var initialEquipment: PriceData? = null

  private val shopItemMap: MutableMap<String, ShopItem> = mutableMapOf()

  fun load(config: FileConfiguration) {
//    systemConfig = config.getSerializable("SystemConfig", SystemConfig::class.java)!!
    val system = config.getConfigurationSection("system")!!

    range = system.getDouble("range")
    checkDuration = system.getDouble("check_duration")

    matchingCompleteAfterWating = system.getDouble("matching_complete_after_wating")
    matchingCompleteTeleportDuration = system.getInt("matching_complete_teleport_duration")
    roundStartTeleportDelay = system.getInt("round_start_teleport_delay")
    roundTitleDisplayDuration = system.getInt("round_title_display_duration")
    buyPhaseDuration = system.getInt("buy_phase_duration")
    countdownDuration = system.getInt("countdown_duration")
    roundTimeoutDuration = system.getInt("round_timeout_duration")
    interRoundDelay = system.getInt("inter_round_delay")

    val messagesConfig = config.getConfigurationSection("messages")!!
    val mcaw = messagesConfig.getString("matching_complete_after_wating")!!
    _messages = Messages(mcaw)
    val scoreSection = config.getConfigurationSection("score")!!
    _score =
      Score(
        scoreSection.getInt("winner_of_round"),
        scoreSection.getInt("loser_of_round"),
        scoreSection.getInt("winner_of_game"),
        scoreSection.getInt("loser_of_game"),
        scoreSection.getInt("get_spot"),
        scoreSection.getInt("after_get_per_sec"),
        scoreSection.getInt("killed"),
        scoreSection.getInt("killer")
      )

    fun toLoc(loc: String): Location {
      val split = loc.split("/", ",")
      assert(split.size == 3)
      return Location(
        null,
        split[0].toDouble(),
        split[1].toDouble(),
        split[2].toDouble()
      )
    }

    val worldsConfig = config.getConfigurationSection("worlds")!!
    for (worldName in worldsConfig.getKeys(false)) {
      val locs = mutableListOf<SpotData>()
      var id = 0
      val worldConfig = worldsConfig.getConfigurationSection(worldName)!!
      val radius = worldConfig.getDouble("range", range)
      val spotsList = worldConfig.getMapList("spots")
      val type = GameType.fromString(worldConfig.getString("type")!!)
      val defaultBlueLocation = toLoc(worldConfig.getString("default_pos_blue", "0/0/0")!!)
      val defaultRedLocation = toLoc(worldConfig.getString("default_pos_red", "0/0/0")!!)
      val defaultLocation = toLoc(worldConfig.getString("default_pos", "0/0/0")!!)
      for (spot in spotsList) {
        val name: String = spot["name"] as String
        val pos = toLoc(spot["pos"] as String)
        locs.add(SpotData(name, pos, radius, GameTeam.NEUTRAL, .0))
        id++
      }

      val barriers = worldConfig.getConfigurationSection("barriers")!!

      val area =
        Area(
          toLoc(barriers.getString("fill", "0/0/0")!!),
          toLoc(barriers.getString("remove", "0/0/0")!!)
        )

//      plugin.logger.info("Loaded ${worldConfig.get("barriers")}")
      battleWorlds[worldName] =
        WorldType(worldName, type, defaultBlueLocation, defaultRedLocation, defaultLocation, locs, area)
      plugin.logger.info { battleWorlds.toString() }
    }

    val shopConfig = config.getConfigurationSection("shop")
    if (shopConfig == null) {
      plugin.logger.warning("shop section is not found")
      return
    }
    for (shopKey in shopConfig.getKeys(false)) {
      val shopItem = shopConfig.getConfigurationSection(shopKey)
      if (shopItem == null) {
        plugin.logger.warning("$shopKey section is not found")
        continue
      }
      val price = shopItem.getInt("price")
      val lore = shopItem.getStringList("lore")
      shopItemMap[shopKey] = ShopItem(shopKey, price, lore)
    }

    val lobbyWorld = config.getString("lobby_world")!!
    val lobbyLoc =
      config
        .getString("lobby_pos", "0/0/0")!!
        .replace("/", ",")
        .split(",")
        .apply { assert(size == 3) }

    lobbyLocation =
      Location(
        plugin.server.getWorld(lobbyWorld),
        lobbyLoc[0].toDouble(),
        lobbyLoc[1].toDouble(),
        lobbyLoc[2].toDouble()
      )

    val ieConf = config.getConfigurationSection("initial_equipment")!!
    initialEquipment =
      PriceData(
        ieConf.getInt("base"),
        ieConf.getInt("round1.winner"),
        ieConf.getInt("round1.winner"),
        ieConf.getInt("round2.winner"),
        ieConf.getInt("round2.loser"),
        ieConf.getInt("round3.winner"),
        ieConf.getInt("round3.loser"),
        ieConf.getInt("round4.winner"),
        ieConf.getInt("round4.loser")
      )
  }

  fun getBattleWorlds(): Map<String, WorldType> = battleWorlds.toMap()

  fun getAllSpots(): List<SpotData> =
    battleWorlds.values
      .map { it.spots }
      .flatten()
      .toList()

  fun getWeapons() = shopItemMap.toMap()
}

class SystemConfig(
  val range: Int,
  val checkDuration: Int,
  val matchingCompleteTeleportDuration: Int,
  val roundStartTeleportDelay: Int,
  val roundTitleDisplayDuration: Int,
  val buyPhaseDuration: Int,
  val countdownDuration: Int,
  val roundTimeoutDuration: Int,
  val interRoundDelay: Int
) : ConfigurationSerializable {
  override fun serialize(): Map<String?, Any?> {
    val map = mutableMapOf<String?, Any?>()
    map["range"] = range
    map["check_duration"] = checkDuration
    map["matching_complete_teleport_duration"] = matchingCompleteTeleportDuration
    map["round_start_teleport_delay"] = roundStartTeleportDelay
    map["round_title_display_duration"] = roundTitleDisplayDuration
    map["buy_phase_duration"] = buyPhaseDuration
    map["countdown_duration"] = countdownDuration
    map["round_timeout_duration"] = roundTimeoutDuration
    map["inter_round_delay"] = interRoundDelay
    return map.toMap()
  }

  companion object {
    @JvmStatic
    fun deserialize(map: Map<String?, Any?>): SystemConfig =
      SystemConfig(
        map["range"] as Int,
        map["check_duration"] as Int,
        map["matching_complete_teleport_duration"] as Int,
        map["round_start_teleport_delay"] as Int,
        map["round_title_display_duration"] as Int,
        map["buy_phase_duration"] as Int,
        map["countdown_duration"] as Int,
        map["round_timeout_duration"] as Int,
        map["inter_round_delay"] as Int
      )
  }
}
