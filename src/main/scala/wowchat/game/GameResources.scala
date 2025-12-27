package wowchat.game

import scala.io.Source

object GameResources {

  lazy val AREA: Map[Int, String] = readIDNameFile("pre_cata_areas.csv")

  // Not used in Vanilla/Turtle WoW
  lazy val ACHIEVEMENT: Map[Int, String] = Map.empty

  private def readIDNameFile(file: String) = {
    Source
      .fromResource(file)
      .getLines
      .map(str => {
        val splt = str.split(",", 2)
        splt(0).toInt -> splt(1)
      })
      .toMap
  }
}
