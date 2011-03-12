package org.spiffy.http

import org.spiffy.actor._
import akka.actor.Actor


/**
 * Represents a pool of Router actors that is:
 * with DefaultScalingLoadBalancer
 * with SmallestMailboxSelectorStrategy
 * with PressureBoundedCapacityStrategy
 * with BoundedCapacity
 * with ActiveFuturesPressure
 * with BasicLinearScaler
 */
class ScalableRouter extends Actor
  with DefaultScalingLoadBalancer
  with SmallestMailboxSelectorStrategy
  with PressureBoundedCapacityStrategy
  with BoundedCapacity
  with ActiveFuturesPressure
  with BasicLinearScaler {

  // ScalingLoadBalancer API
  val factory = new ActorFactory {
    def build = Actor.actorOf[Router].start
  }

  // BoundedCapacity API
  def min = Runtime.getRuntime.availableProcessors
  def max = Runtime.getRuntime.availableProcessors * 16

  def receive = _recvSLB
}

object ScalableRouter {
  private val _actor = {
    val a = Actor.actorOf(new Router).start
    // TODO: link to a supervisor?
    //_supervisor.link(a)
    a
  }

  def apply() = _actor
}
