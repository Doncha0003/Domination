package com.tsubuserver.zonerush.data

import org.bukkit.Location
import org.bukkit.scheduler.BukkitRunnable

data class SpotData(
  val id: String,
  val centerLocation: Location,
  val radius: Double,
  var currentTeam: GameTeam,
  var currentGauge: Double,
  var currentTask: BukkitRunnable? = null,
  var isDraw: Boolean = true,
  var isAnnounced: Boolean = false
) {
  fun isInside(loc: Location): Boolean {
    centerLocation.world = loc.world
    return centerLocation.distanceSquared(loc) <= ((radius * radius) / 4)
  }
}
