package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.manager.game.GameManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.boss.KeyedBossBar
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin

class ZoneRush : JavaPlugin() {
  companion object {
    private lateinit var instance: ZoneRush

    fun getInstance(): ZoneRush = instance
  }

  @Suppress("UnstableApiUsage")
  override fun onEnable() {
    // Plugin startup logic
    instance = this

    ConfigurationSerialization.registerClass(SystemConfig::class.java)

    saveDefaultConfig()
    server.bossBars.forEach {
      if (it is KeyedBossBar && it.key.namespace == "zonerush") {
        it.removeAll()
      }
    }
    Config.load(config)

    lifecycleManager
      .registerEventHandler(LifecycleEvents.COMMANDS) {
        it.registrar().register(Command.create())
        it.registrar().register(Command.createShorter())
      }

    GameManager.initialize()

    server.pluginManager.registerEvents(GameEventListener, this)
    server.pluginManager.registerEvents(GuiHandler, this)
    server.pluginManager.registerEvents(EquipmentEffectsListener(), this)
    server.pluginManager.registerEvents(ExplosiveBowListener(), this)
    ScoreListener

    logger.info("ZoneRush plugin enabled")
  }

  override fun onDisable() {
    // Plugin shutdown logic
    GameManager.shutdown()
    BossbarHandler.shutdown()
    logger.info("ZoneRush plugin disabled")
  }
}
