package com.tsubuserver.zonerush.task

import com.tsubuserver.zonerush.data.GameTeam.*
import com.tsubuserver.zonerush.data.SpotData
import com.tsubuserver.zonerush.manager.game.GameInstance
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class SpotChangeTask(private val gameInstance: GameInstance) : BukkitRunnable() {
  var worldType = gameInstance.worldType
  override fun run() {
    for (spot in worldType.spots) {
      spawnCaptureParticles(
        spot
      )
    }
  }

  private fun spawnCaptureParticles(spot: SpotData) {
    val location = spot.centerLocation
    val color = when (spot.currentTeam) {
      RED -> Color.RED
      BLUE -> Color.BLUE
      NEUTRAL -> Color.WHITE
    }
    val world = Bukkit.getWorld(worldType.worldName)!!

    val display = world.spawnEntity(
      location.clone().apply { this.world = world }.add(0.0, 3.0, 0.0),
      EntityType.BLOCK_DISPLAY,
    )
    display as BlockDisplay
    display.block = when (spot.currentTeam) {
      RED -> Material.RED_BANNER
      BLUE -> Material.BLUE_BANNER
      NEUTRAL -> Material.WHITE_BANNER
    }.createBlockData()
    display.brightness = Display.Brightness(15, 0)
    display.isGlowing = true
    display.glowColorOverride = color

    val transformation = Transformation(
      Vector3f(-0.5f, 0f, -0.5f),
      Quaternionf(),
      Vector3f(1.5f, 2f, 1.5f),      // 3倍サイズ
      Quaternionf()
    )
    display.transformation = transformation

    val particle = Particle.DustOptions(color, 2f)

    val spiralRadius = 1.5  // 螺旋の半径
    val flagHeight = 4.0    // 旗の高さ（2f * 2倍スケール = 4ブロック）
    val spiralTurns = 2     // 螺旋の回転数

    for (i in 0 until 60) { // 60個のパーティクルで螺旋を描く
      val progress = i / 60.0  // 0.0 から 1.0
      val angle = progress * spiralTurns * 2 * Math.PI  // 螺旋の角度
      val height = progress * flagHeight  // 旗の高さ分だけ上昇

      val x = location.x + spiralRadius * cos(angle)
      val z = location.z + spiralRadius * sin(angle)
      val y = location.y + 3.0 + height  // 旗の開始位置(+3.0)から旗の高さ分

      val particleLoc = location.clone().apply {
        this.world = world
        this.x = x
        this.y = y
        this.z = z
      }
      world.spawnParticle(Particle.DUST, particleLoc, 1, 0.0, 0.0, 0.0, 0.0, particle)
    }

    val size = spot.radius
    for (i in 0 until 360 step 10) {
      val radians = Math.toRadians(i.toDouble())
      val x = location.x + (size / 2) * cos(radians)
      val z = location.z + (size / 2) * sin(radians)
      val world = Bukkit.getWorld(worldType.worldName)!!
      val particleLoc = location.clone().apply {
        this.world = world
        this.x = x
        this.z = z
      }
      world.spawnParticle(Particle.DUST, particleLoc, 20, 0.0, 0.0, 0.0, 0.0, particle)
    }
  }

}
