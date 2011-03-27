package org.spiffy.http

import akka.actor.{Actor,ActorRef}

/**
 * Controllers that have BeforeHooks as a trait
 * will have their before actor messaged before
 * they are messages.
 */
trait BeforeHooks {
  val before:Array[ActorRef]
}

/**
 * Extractor that figures out if a certain hook should
 * try to invoke the next hook in the array of hooks for
 * a controller.
 */
object CanForward {
  def unapply(h:HookMsg) : Option[HookMsg] = {
    if (h.curHook + 1 < h.hooks.length) Some(h)
    else None
  }
}

/**
 * Misc hook helpers.
 */
object HookHelpers {

  /**
   * Partial function that tries to forward the request
   * to the next hook in the array of hooks.
   * Used as part of (forward orElse call)(h)
   */
  val forward : PartialFunction[HookMsg, Boolean] = { 
    case CanForward(msg) => {
      val hook = msg.hooks(msg.curHook + 1)
      hook ! HookMsg(msg.curHook + 1, msg.hooks, msg.ctrl, msg.msg)
      true
    }   
  }

  /**
   * Partial function that messages the controller with the
   * request.
   * Used as part of (forward orElse call)(h)
   */
  val call : PartialFunction[HookMsg, Boolean] = {
    case msg => {
      msg.ctrl ! msg.msg
      true
    }    
  }
}

