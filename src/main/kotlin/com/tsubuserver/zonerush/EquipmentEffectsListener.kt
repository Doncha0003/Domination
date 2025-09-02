package com.tsubuserver.zonerush

import org.bukkit.NamespacedKey
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class EquipmentEffectsListener : Listener {
  private val plugin = ZoneRush.getInstance()
  private val activeEffects = mutableMapOf<Player, MutableSet<PotionEffectType>>()
  private val continuousEffectTasks = mutableMapOf<Player, BukkitTask>()

  @EventHandler
  fun onInventoryClick(event: InventoryClickEvent) {
    val player = event.whoClicked as? Player ?: return

    // 装備スロットの変更を検知
    if (isArmorSlot(event.slot)) {
      // 少し遅延させて装備変更後の状態をチェック
      object : BukkitRunnable() {
        override fun run() {
          updatePlayerEffects(player)
        }
      }.runTaskLater(plugin, 1L)
    }
  }

  @EventHandler
  fun onItemHeld(event: PlayerItemHeldEvent) {
    // 手に持つアイテムが変わった時もチェック
    object : BukkitRunnable() {
      override fun run() {
        updatePlayerEffects(event.player)
      }
    }.runTaskLater(plugin, 1L)
  }

  @EventHandler
  fun onPlayerQuit(event: PlayerQuitEvent) {
    activeEffects.remove(event.player)
    continuousEffectTasks[event.player]?.cancel()
    continuousEffectTasks.remove(event.player)
  }

  @EventHandler
  fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
    val attacker = event.damager as? Player ?: return
    val weapon = attacker.inventory.itemInMainHand

    if (weapon.itemMeta?.persistentDataContainer?.has(
        NamespacedKey(plugin, "vampire_weapon"),
        PersistentDataType.BOOLEAN
      ) == true
    ) {
      handleVampireWeapon(attacker, weapon, event.finalDamage)
    }
  }

  @EventHandler
  fun onProjectileHit(event: ProjectileHitEvent) {
    val arrow = event.entity as? Arrow ?: return
    val shooter = arrow.shooter as? Player ?: return

    // 矢にメタデータが付いているかチェック
    if (arrow.persistentDataContainer.has(
        NamespacedKey(plugin, "explosive_arrow"),
        PersistentDataType.BOOLEAN
      )
    ) {
      handleExplosiveArrow(arrow, shooter)
    }
  }

  private fun isArmorSlot(slot: Int): Boolean {
    // 防具スロット: 36=ブーツ, 37=レギンス, 38=チェスト, 39=ヘルメット
    return slot in 36..39
  }

  private fun updatePlayerEffects(player: Player) {
    // 既存の継続効果タスクをキャンセル
    continuousEffectTasks[player]?.cancel()
    continuousEffectTasks.remove(player)

    // 現在の装備時効果をクリア
    activeEffects[player]?.forEach { effectType ->
      player.removePotionEffect(effectType)
    }
    activeEffects[player]?.clear()

    // 新しい装備の効果を収集
    val equipmentEffects = mutableListOf<EquipmentEffect>()

    // 全装備スロットをチェック
    listOf(
      player.inventory.helmet,
      player.inventory.chestplate,
      player.inventory.leggings,
      player.inventory.boots,
      player.inventory.itemInMainHand,
      player.inventory.itemInOffHand
    ).forEach { item ->
      if (item != null) {
        collectEquipmentEffects(item, equipmentEffects)
      }
    }

    if (equipmentEffects.isNotEmpty()) {
      startContinuousEffects(player, equipmentEffects)
    }
  }

  private fun collectEquipmentEffects(
    item: ItemStack,
    effectsList: MutableList<EquipmentEffect>
  ) {
    val meta = item.itemMeta ?: return
    val container = meta.persistentDataContainer

    val effectsData =
      container.get(
        NamespacedKey(plugin, "equipment_effects"),
        PersistentDataType.STRING
      ) ?: return

    effectsData.split(";").forEach { effectString ->
      try {
        val parts = effectString.split(":")
        if (parts.size >= 3) {
          @Suppress("DEPRECATION")
          val effectType = PotionEffectType.getByName(parts[0].uppercase())
          val amplifier = parts[1].toInt()
          val duration = parts[2].toInt()

          if (effectType != null) {
            effectsList.add(EquipmentEffect(effectType, amplifier, duration))
          }
        }
      } catch (e: Exception) {
        plugin.logger.warning("Failed to parse equipment effect: $effectString")
      }
    }
  }

  private fun startContinuousEffects(
    player: Player,
    effects: List<EquipmentEffect>
  ) {
    val newActiveEffects = mutableSetOf<PotionEffectType>()

    // 継続的に効果を適用するタスクを開始
    val task =
      object : BukkitRunnable() {
        override fun run() {
          // プレイヤーがまだオンラインかチェック
          if (!player.isOnline) {
            cancel()
            return
          }

          // 現在の装備を再チェックして、まだ同じ効果を持つ装備があるかチェック
          val currentEffects = mutableListOf<EquipmentEffect>()
          listOf(
            player.inventory.helmet,
            player.inventory.chestplate,
            player.inventory.leggings,
            player.inventory.boots,
            player.inventory.itemInMainHand,
            player.inventory.itemInOffHand
          ).forEach { item ->
            if (item != null) {
              collectEquipmentEffects(item, currentEffects)
            }
          }

          // 現在の装備に基づいて効果を適用
          currentEffects.forEach { effect ->
            val actualDuration = if (effect.duration == -1) 40 else minOf(effect.duration, 40) // 最大2秒
            val potionEffect =
              PotionEffect(
                effect.effectType,
                actualDuration,
                effect.amplifier,
                false,
                false,
                false
              )
            player.addPotionEffect(potionEffect)
            newActiveEffects.add(effect.effectType)
          }
        }
      }

    // 1秒ごとに実行（20tick = 1秒）
    val scheduledTask = task.runTaskTimer(plugin, 0L, 20L)
    continuousEffectTasks[player] = scheduledTask
    activeEffects[player] = newActiveEffects
  }

  private fun handleVampireWeapon(
    player: Player,
    weapon: ItemStack,
    damage: Double
  ) {
    val meta = weapon.itemMeta!!
    val container = meta.persistentDataContainer

    // 回復倍率を取得（デフォルト30%）
    val healPercentage =
      container.get(
        NamespacedKey(plugin, "vampire_heal_percentage"),
        PersistentDataType.DOUBLE
      ) ?: plugin.config.getDouble("vampire_weapon.heal_percentage", 0.3) // デフォルト30%

    // 与えたダメージの設定%分を回復
    val healAmount = damage * healPercentage

    val currentHealth = player.health
    val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

    // 体力回復（最大体力を超えない範囲で）
    val newHealth = minOf(currentHealth + healAmount, maxHealth)
    player.health = newHealth
  }

  private fun handleExplosiveArrow(
    arrow: Arrow,
    shooter: Player
  ) {
    val location = arrow.location

    // 爆発効果を作成（地形破壊なし、ダメージあり）
    location.world?.createExplosion(
      location,
      3.0f, // 爆発力（半径2ブロック相当）
      false, // 地形破壊なし
      false // 火災なし
    )

    // 矢を削除
    arrow.remove()
  }

  // 装備効果を表現するデータクラス
  private data class EquipmentEffect(
    val effectType: PotionEffectType,
    val amplifier: Int,
    val duration: Int
  )
}
