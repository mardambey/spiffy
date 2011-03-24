package org.spiffy

/**
 * Misc helpers and utilities.
 */
object Helpers {
  /**
   * Gives the companion object of the given string as an instane of the given manifest.
   */
  def companion[T](name : String)(implicit man: Manifest[T]) : T = 
    Class.forName(name + "$").getField("MODULE$").get(man.erasure).asInstanceOf[T]
}
