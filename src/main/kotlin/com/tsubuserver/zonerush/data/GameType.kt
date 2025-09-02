package com.tsubuserver.zonerush.data

enum class GameType(
  val min: Int,
  val max: Int,
  val type: String
) {
  VS_1(2, 2, "1v1"),
  VS_2(4, 8, "2v2"),
  UNDEFINED(8, Int.MAX_VALUE, "UNDEFINED");

  companion object {
    fun fromString(str: String): GameType = entries.find { it.type == str } ?: UNDEFINED
  }
}
