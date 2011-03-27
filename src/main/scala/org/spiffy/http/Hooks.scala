package org.spiffy.http

import akka.actor.{Actor,ActorRef}

/**
 * Controllers that have BeforeHooks as a trait
 * will have their before actor messaged before
 * they are messages.
 */
trait BeforeHooks {
  val before:ActorRef
}


