package com.tsubuserver.zonerush.data

import com.tsubuserver.zonerush.ZoneRush
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ColorableArmorMeta
import org.bukkit.persistence.PersistentDataType

val KEEP = NamespacedKey.fromString("must_keep", ZoneRush.getInstance())!!

private fun coloredLeather(
  material: Material,
  color: Color
): ItemStack {
  val stack = ItemStack(material)
  val meta = stack.itemMeta as ColorableArmorMeta
  meta.setColor(color)
  meta.isUnbreakable = true
//  meta.persistentDataContainer.set(
//    KEEP,
//    PersistentDataType.BOOLEAN,
//    true
//  )
  stack.editPersistentDataContainer {
    it.set(
      KEEP,
      PersistentDataType.BOOLEAN,
      true
    )
  }
  stack.itemMeta = meta
  return stack
}

enum class GameTeam(
  val displayName: Component,
  val color: TextColor,
  val bossColor: BarColor,
  val initEquip: Map<EquipmentSlot, ItemStack> = mapOf()
) {
  RED(
    Component.text("赤チーム"),
    NamedTextColor.RED,
    BarColor.RED,
    mapOf(
      EquipmentSlot.HEAD to coloredLeather(Material.LEATHER_HELMET, Color.RED),
      EquipmentSlot.CHEST to coloredLeather(Material.LEATHER_CHESTPLATE, Color.RED),
      EquipmentSlot.LEGS to coloredLeather(Material.LEATHER_LEGGINGS, Color.RED),
      EquipmentSlot.FEET to coloredLeather(Material.LEATHER_BOOTS, Color.RED)
    )
  ),
  BLUE(
    Component.text("青チーム"),
    NamedTextColor.BLUE,
    BarColor.BLUE,
    mapOf(
      EquipmentSlot.HEAD to coloredLeather(Material.LEATHER_HELMET, Color.BLUE),
      EquipmentSlot.CHEST to coloredLeather(Material.LEATHER_CHESTPLATE, Color.BLUE),
      EquipmentSlot.LEGS to coloredLeather(Material.LEATHER_LEGGINGS, Color.BLUE),
      EquipmentSlot.FEET to coloredLeather(Material.LEATHER_BOOTS, Color.BLUE)
    )
  ),
  NEUTRAL(Component.text("未取得"), NamedTextColor.WHITE, BarColor.WHITE) // 未取得陣地用
  ;

  fun colored() = displayName.color(color)

  fun initialEquip(player: Player) {
    player.equipment.helmet = initEquip[EquipmentSlot.HEAD]!!
    player.equipment.chestplate = initEquip[EquipmentSlot.CHEST]!!
    player.equipment.leggings = initEquip[EquipmentSlot.LEGS]!!
    player.equipment.boots = initEquip[EquipmentSlot.FEET]!!
  }

  fun deserialize() = LegacyComponentSerializer.legacySection().serialize(colored())
}
