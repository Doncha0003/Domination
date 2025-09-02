package com.tsubuserver.zonerush

import com.tsubuserver.zonerush.manager.game.GameInstance
import com.tsubuserver.zonerush.manager.game.GameManager
import me.deecaad.weaponmechanics.WeaponMechanics
import me.deecaad.weaponmechanics.WeaponMechanicsAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.set
import xyz.xenondevs.invui.gui.structure.Markers
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.item.impl.controlitem.PageItem
import xyz.xenondevs.invui.window.AbstractSingleWindow
import xyz.xenondevs.invui.window.Window
import java.util.*

class BackItem : PageItem(false) {
  override fun getItemProvider(gui: PagedGui<*>): ItemProvider {
    val builder = ItemBuilder(Material.RED_STAINED_GLASS_PANE)
    builder
      .setDisplayName("Previous page")
      .addLoreLines(
        if (gui.hasPreviousPage()) {
          "Go to page " + gui.currentPage + "/" + gui.pageAmount
        } else {
          "You can't go further back"
        }
      )
    return builder
  }
}

class ForwardItem : PageItem(true) {
  override fun getItemProvider(gui: PagedGui<*>): ItemProvider {
    val builder = ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
    builder
      .setDisplayName("Next page")
      .addLoreLines(
        if (gui.hasNextPage()) {
          "Go to page " + (gui.currentPage + 2) + "/" + gui.pageAmount
        } else {
          "There are no more pages"
        }
      )
    return builder
  }
}

object GuiHandler : Listener {
  val plugin = ZoneRush.getInstance()
  private val windowMap: MutableMap<UUID, Window> = mutableMapOf()
  private val shopItems: MutableList<Pair<ItemStack, Config.ShopItem>> = mutableListOf()
  val availableWeapons: Map<String, Config.ShopItem> = Config.getWeapons()

  private var isSuccessfullyLoaded = false

  fun buildItem(
    player: Player,
    inst: GameInstance?
  ): ItemStack =
    ItemStack(Material.PLAYER_HEAD).apply {
      val meta = itemMeta as SkullMeta
      meta.owningPlayer = player
      meta.displayName(player.displayName().decoration(TextDecoration.ITALIC, false))
      itemMeta = meta

      lore(
        listOf(
          "§r所持金: "
            .toComponent()
            .append(
              (inst?.prices?.getOrDefault(player.uniqueId, 0) ?: 0)
                .toString()
                .toComponent()
                .color(NamedTextColor.GOLD)
            ).append(" pt".toComponent().color(NamedTextColor.GRAY)),
          "▶"
            .toComponent()
            .color(NamedTextColor.GRAY)
            .append("武器にカーソルを合わせると性能と価格が表示されます".toComponent().color(NamedTextColor.WHITE)),
          "▶"
            .toComponent()
            .color(NamedTextColor.GRAY)
            .append("右クリック".toComponent().color(NamedTextColor.RED))
            .append("で武器を購入できます。".toComponent().color(NamedTextColor.WHITE))
        ).map { it.decoration(TextDecoration.ITALIC, false) }
      )
    }

  fun openShop(player: Player) {
    val inst = GameManager.getGameInstance(player.uniqueId)
    if (!player.isOp && inst?.gamePhase != GameManager.GamePhase.BUY_PHASE) return

    load()
    val shopItemList =
      shopItems.map {
        val name = WeaponMechanicsAPI.getWeaponTitle(it.first)
        if (name != null) {
          // WeaponMechanicsの武器の場合
          val stack = WeaponMechanicsAPI.generateWeapon(name)
          stack.lore(
            stack
              .lore()!!
              .toMutableList()
              .apply {
                add("".toComponent())
                add("${it.second.price} pt".toComponent())
              }
          )
          SimpleItem(stack)
        } else {
          // 通常のアイテムの場合
          val stack = it.first.clone()
          stack.lore(
            (stack.lore() ?: listOf<Component>())
              .toMutableList()
              .apply {
                add("".toComponent())
                add("${it.second.price} pt".toComponent().decoration(TextDecoration.ITALIC, false))
              }
          )
          SimpleItem(stack)
        }
      }
    val gui =
      PagedGui
        .items()
        .setStructure(
          "# # # # H # # # #",
          "# x x x x x x x #",
          "# x x x x x x x #",
          "# # # < # > # # #"
        ).addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
        .addIngredient('#', SimpleItem(ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)))
        .addIngredient('<', BackItem())
        .addIngredient('>', ForwardItem())
        .addIngredient('H', buildItem(player, inst))
        .setContent(shopItemList)
        .build()

    val window =
      Window
        .single()
        .setViewer(player)
        .setTitle("Shop")
        .setGui(gui)
        .build()

    windowMap[player.uniqueId] = window
    window.open()
  }

  fun load(force: Boolean = false) {
    if (isSuccessfullyLoaded && !force) return
    shopItems.clear()
    val handler = WeaponMechanics.getInstance().weaponConfigurations
    var index = 0

    fun getItemStack(pair: Pair<String, Config.ShopItem>): ItemStack? {
      val key = pair.first
      val config = plugin.config

      val weapon = handler.getObject("$key.Info.Weapon_Item", ItemStack::class.java)

      if (weapon != null) return weapon

      val itemSection = config.getConfigurationSection("shop.$key")

      // materialキー優先、それがなければ従来のロジック
      val mat =
        itemSection
          ?.getString("material")
          ?.let { Material.matchMaterial(it.uppercase()) }
          ?: when {
            key.contains("SPLASH_POTION", ignoreCase = true) -> Material.SPLASH_POTION
            key.contains("LINGERING_POTION", ignoreCase = true) -> Material.LINGERING_POTION
            key.contains("TIPPED_ARROW", ignoreCase = true) -> Material.TIPPED_ARROW
            else -> Material.matchMaterial(key)
          }
      if (mat == null) return null

      val stack =
        ItemStack(mat).apply {
          val lore =
            pair.second.lore
              .map { it.toComponent() }
              .map { it.decoration(TextDecoration.ITALIC, false) }
          lore(
            (lore() ?: listOf<Component>()) + lore
          )
        }

      if (itemSection != null) {
        val meta = stack.itemMeta!!
        val name = itemSection.getString("name")?.let { it.toComponent() }

        if (name != null) meta.customName(name)

        val enchantSection = itemSection.getConfigurationSection("enchantments")
        enchantSection?.getKeys(false)?.forEach { enchantName ->
          val level = enchantSection.getInt(enchantName)
          val enchant =
            Enchantment.getByKey(NamespacedKey.minecraft(enchantName.lowercase()))
              ?: Enchantment.getByName(enchantName.uppercase())
          if (enchant != null) {
            meta.addEnchant(enchant, level, true)
          } else {
            plugin.logger.warning("Unknown enchantment: $enchantName for item $key")
          }
        }

        // ★ 装備時ポーション効果の設定 ★
        val equipmentEffects = itemSection.getMapList("equipmentEffects")
        if (equipmentEffects.isNotEmpty()) {
          val effectsData = mutableListOf<String>()

          equipmentEffects.forEach { effectMap ->
            try {
              val effectType = effectMap["type"] as String
              val amplifier = effectMap["amplifier"] as Int
              val duration = effectMap["duration"] as Int

              // メタデータとして保存（後でイベントハンドラーで使用）
              effectsData.add("$effectType:$amplifier:$duration")
            } catch (e: Exception) {
              plugin.logger.warning("Invalid equipment effect data in $key: $effectMap")
            }
          }

          if (effectsData.isNotEmpty()) {
            val cont = meta.persistentDataContainer
            cont.set(
              NamespacedKey(plugin, "equipment_effects"),
              PersistentDataType.STRING,
              effectsData.joinToString(";")
            )
          }
        }

        val isVampireWeapon = itemSection.getBoolean("vampireWeapon", false)
        if (isVampireWeapon) {
          val cont = meta.persistentDataContainer
          cont.set(NamespacedKey(plugin, "vampire_weapon"), PersistentDataType.BOOLEAN, true)

          // 回復倍率を設定（デフォルト30%）
          val healPercentage =
            itemSection.getDouble(
              "vampireHealPercentage",
              plugin.config.getDouble("vampire_weapon.heal_percentage", 0.3)
            )
          cont.set(NamespacedKey(plugin, "vampire_heal_percentage"), PersistentDataType.DOUBLE, healPercentage)
        }

        // 爆発弓の設定
        val isExplosiveBow = itemSection.getBoolean("explosiveBow", false)
        if (isExplosiveBow) {
          val cont = meta.persistentDataContainer
          cont.set(NamespacedKey(plugin, "explosive_bow"), PersistentDataType.BOOLEAN, true)
        }

        if (meta is PotionMeta) {
          itemSection.getString("potionEffect")?.let { potionTypeName ->
            try {
              val potionType = PotionType.valueOf(potionTypeName.uppercase())
              meta.basePotionType = potionType
            } catch (e: IllegalArgumentException) {
              plugin.logger.warning("Unknown potion type: $potionTypeName for item $key")
            }
          }

          val potionEffects = itemSection.getMapList("potionEffects")
          potionEffects.forEach { effectMap ->
            try {
              val effectType = PotionEffectType.getByName((effectMap["type"] as String).uppercase())
              if (effectType != null) {
                val duration = effectMap["duration"] as Int
                val amplifier = effectMap["amplifier"] as Int
                val effect = PotionEffect(effectType, duration, amplifier)
                meta.addCustomEffect(effect, true)
              }
            } catch (e: Exception) {
              plugin.logger.warning("Invalid potion effect data in $key: $effectMap")
            }
          }
        }

        val customModelData = itemSection.getInt("customModelData")
        if (customModelData != 0) {
          meta.setCustomModelData(customModelData)
        }

        val isUnbreakable = itemSection.getBoolean("unbreakable", false)
        if (isUnbreakable) {
          meta.isUnbreakable = true
        }

        // --- Paper API 1.21.4対応のattribute機能 ---
        val attributes = itemSection.getMapList("attributes")
        attributes.forEach { attrMap ->
          try {
            val attrName = attrMap["name"] as? String ?: return@forEach

            // Attribute名の正規化 - Paper API 1.21.4対応
            val attribute =
              when {
                // 完全一致で検索
                attrName.equals("ATTACK_DAMAGE", true) -> Attribute.ATTACK_DAMAGE
                attrName.equals("ATTACK_SPEED", true) -> Attribute.ATTACK_SPEED
                attrName.equals("MOVEMENT_SPEED", true) -> Attribute.MOVEMENT_SPEED
                attrName.equals("MAX_HEALTH", true) -> Attribute.MAX_HEALTH
                attrName.equals("LUCK", true) -> Attribute.LUCK
                attrName.equals("ARMOR", true) -> Attribute.ARMOR
                attrName.equals("ARMOR_TOUGHNESS", true) -> Attribute.ARMOR_TOUGHNESS
                attrName.equals("KNOCKBACK_RESISTANCE", true) -> Attribute.KNOCKBACK_RESISTANCE
                attrName.equals("FOLLOW_RANGE", true) -> Attribute.FOLLOW_RANGE

                attrName.startsWith("GENERIC.", true) -> {
                  val simpleName = attrName.substringAfter(".")
                  when (simpleName.uppercase()) {
                    "ATTACK_DAMAGE" -> Attribute.ATTACK_DAMAGE
                    "ATTACK_SPEED" -> Attribute.ATTACK_SPEED
                    "MOVEMENT_SPEED" -> Attribute.MOVEMENT_SPEED
                    "MAX_HEALTH" -> Attribute.MAX_HEALTH
                    "LUCK" -> Attribute.LUCK
                    "ARMOR" -> Attribute.ARMOR
                    "ARMOR_TOUGHNESS" -> Attribute.ARMOR_TOUGHNESS
                    "KNOCKBACK_RESISTANCE" -> Attribute.KNOCKBACK_RESISTANCE
                    "FOLLOW_RANGE" -> Attribute.FOLLOW_RANGE
                    else -> null
                  }
                }

                // NamespacedKey形式の場合
                attrName.contains(":") -> {
                  try {
                    val namespacedKey = NamespacedKey.fromString(attrName.lowercase())
                    Registry.ATTRIBUTE
//                    Attribute.values()
                      .firstOrNull { it.key == namespacedKey }
                  } catch (e: Exception) {
                    null
                  }
                }

                else -> null
              }

            if (attribute == null) {
              plugin.logger.warning("Unknown attribute: $attrName for item $key")
              return@forEach
            }

            val amount = (attrMap["amount"] as Number).toDouble()
            val operation = AttributeModifier.Operation.valueOf((attrMap["operation"] as String).uppercase())

            // Paper API 1.21.4では EquipmentSlot を使用（安定版）
            val slot =
              try {
                val slotName = (attrMap["slot"] as String).uppercase()
                when (slotName) {
                  "HAND", "MAINHAND" -> EquipmentSlotGroup.HAND
                  "OFFHAND" -> EquipmentSlotGroup.OFFHAND
                  "HEAD", "HELMET" -> EquipmentSlotGroup.HEAD
                  "CHEST", "CHESTPLATE" -> EquipmentSlotGroup.CHEST
                  "LEGS", "LEGGINGS" -> EquipmentSlotGroup.LEGS
                  "FEET", "BOOTS" -> EquipmentSlotGroup.FEET
                  else -> EquipmentSlotGroup.HAND
                }
              } catch (e: Exception) {
                EquipmentSlotGroup.HAND
              }

            val uuid =
              (attrMap["uuid"] as? String)
                ?.let {
                  try {
                    UUID.fromString(it)
                  } catch (_: Exception) {
                    null
                  }
                }
                ?: UUID.randomUUID()

            // Paper API 1.21.4対応 - UUIDベースのコンストラクタを使用
            val modifier =
              AttributeModifier(
                NamespacedKey.fromString(uuid.toString())!!,
//                "${key}_${attrName.lowercase()}",
                amount,
                operation,
                slot
              )

            meta.addAttributeModifier(attribute, modifier)
          } catch (e: Exception) {
            plugin.logger.warning("Invalid attribute data for item $key: $attrMap - Error: ${e.message}")
            e.printStackTrace()
          }
        }
        // --- attribute機能ここまで ---

        val amount = itemSection.getInt("amount", 1)
        stack.amount = amount

        // カスタム耐久値の設定
        val customDurability = itemSection.getInt("customDurability", -1)
        if (customDurability > 0) {
          val maxDurability = stack.type.maxDurability
          val damage = maxDurability - customDurability
          if (damage >= 0 && damage < maxDurability) {
            val damageable = meta as? org.bukkit.inventory.meta.Damageable
            damageable?.damage = damage
          }
        }
//        meta.displayName(Component.text("ここに名前入れるんだよ").color(NamedTextColor.GREEN))

        stack.itemMeta = meta
      }

      return stack
    }

    for ((key, value) in availableWeapons) {
      val stack = getItemStack(key to value)
      if (stack == null) {
        plugin.logger.warning("$key's item cannot found")
        continue
      }
      val meta = stack.itemMeta
      val cont = meta.persistentDataContainer
      cont.set(NamespacedKey(plugin, "zr_id"), PersistentDataType.INTEGER, index)
      cont.set(NamespacedKey(plugin, "zr_shop"), PersistentDataType.BOOLEAN, true)
      stack.itemMeta = meta
      shopItems.add(stack to value)
      index++
    }
    plugin.logger.info("Loaded ${shopItems.size} items")
    if (shopItems.isNotEmpty()) {
      isSuccessfullyLoaded = true
    }
  }

  @EventHandler
  fun whenPlayerClicks(event: InventoryClickEvent) {
    val player = event.whoClicked as Player
    windowMap[player.uniqueId] ?: return
    val item = event.currentItem ?: return
    val zrCont =
      item.itemMeta?.persistentDataContainer
        ?: return
    val zrShop = zrCont.get(NamespacedKey(plugin, "zr_shop"), PersistentDataType.BOOLEAN) ?: return
    if (zrShop && event.clickedInventory?.type !== InventoryType.PLAYER) {
      if (event.click !== ClickType.RIGHT) {
        player.sendMessage("You can buy RIGHT_CLICK only.")
        return
      }
      val zrId = zrCont.get(NamespacedKey(plugin, "zr_id"), PersistentDataType.INTEGER) ?: return
      purchase(player, item, zrId)
    }
  }

  private fun purchase(
    player: Player,
    item: ItemStack,
    id: Int
  ) {
    val inst = GameManager.getGameInstance(player.uniqueId)!!
    val price = shopItems[id].second.price
    val money = inst.prices.getOrDefault(player.uniqueId, 0)

    if (money < price) {
      player.sendMessage("money < price: $money < $price")
      return
    }

    inst.prices[player.uniqueId] = money - price
    val w = windowMap[player.uniqueId]!! as AbstractSingleWindow
    w.gui[4] = SimpleItem(buildItem(player, inst))
    w.gui.updateControlItems()

    val name = WeaponMechanicsAPI.getWeaponTitle(item)
    if (name != null) {
      WeaponMechanicsAPI.giveWeapon(name, player)
      player.sendMessage("Bought $name", "Purchased $name for $price pt")
    } else {
      val giveItem = item.clone()
      val meta = giveItem.itemMeta!!
      val container = meta.persistentDataContainer
      container.remove(NamespacedKey(plugin, "zr_id"))
      container.remove(NamespacedKey(plugin, "zr_shop"))
      giveItem.itemMeta = meta

      // 価格表示を削除
      val lore = giveItem.lore()?.toMutableList() ?: mutableListOf()
      if (lore.size >= 2) {
        // 最後の2行（空行と価格行）を削除
        lore.removeAt(lore.size - 1) // 価格行
        lore.removeAt(lore.size - 1) // 空行
        giveItem.lore(lore)
      }

      player.inventory.addItem(giveItem)
      player.sendMessage("Purchased ${giveItem.type.name} for $price pt")
    }
  }
}
