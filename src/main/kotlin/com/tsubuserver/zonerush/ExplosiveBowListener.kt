package com.tsubuserver.zonerush

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.persistence.PersistentDataType

class ExplosiveBowListener : Listener {
  private val plugin = ZoneRush.getInstance()

  @EventHandler
  fun onEntityShootBow(event: EntityShootBowEvent) {
    val shooter = event.entity as? Player ?: return
    val bow = event.bow ?: return

    // 爆発弓かどうかチェック
    if (bow.itemMeta?.persistentDataContainer?.has(
        NamespacedKey(plugin, "explosive_bow"),
        PersistentDataType.BOOLEAN
      ) == true
    ) {
      // 撃った矢に爆発フラグを付ける
      val projectile = event.projectile
      projectile.persistentDataContainer.set(
        NamespacedKey(plugin, "explosive_arrow"),
        PersistentDataType.BOOLEAN,
        true
      )
    }
  }
}
