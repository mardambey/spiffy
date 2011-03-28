package org.spiffy.http

import akka.actor.{Actor,ActorRef}
import org.spiffy.Helpers._
import org.spiffy.http.HookType._
import org.spiffy.http.{ScalateViewHandler => view}

/**
 * Controllers that have BeforeHooks as a trait
 * will have their before actor messaged before
 * they are messages.
 */
trait BeforeHooks {
  val before:Array[ActorRef]
}

/**
 * Controllers that have AfterHooks as a trait
 * will have their after actor called right before
 * they render their view. This is done in the view
 * handler itself.
 */
trait AfterHooks {
  val after:Array[ActorRef]
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

object HasAfterHooks {
  def unapply(s:Spiffy) : Option[HookMsg] = {
    try {
      val comp = companion[AfterHooks](s.ctrl.getActorClass.getName)
    
      if (comp != None) {
	Some(HookMsg(0, comp.after, s, After))
      }
      else None
    } catch {
      case e:Exception => None
    }
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
      hook ! HookMsg(msg.curHook + 1, msg.hooks, msg.spiffy, msg.h)
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
      if (msg.h == Before) msg.spiffy.ctrl ! msg.spiffy
      else view() ! IViewMsg(msg.spiffy)
      true
    }    
  }
}

/**
 * If used by view handlers they will be made be able
 * to handle hooks that run after the controller renders.
 */
trait AfterHookEnabled extends Actor {
  /**
   * Responsible for rendering the view.
   */
  def receive = immediateRender orElse afterHooks orElse render

  def afterHooks : PartialFunction[Any, Unit] = {
    case s @ HasAfterHooks(h) => {
      val comp = companion[AfterHooks](s.ctrl.getActorClass.getName)      
      // run the first hook
      comp.after(0) ! HookMsg(0, comp.after, s, After)
    }
  }

  def immediateRender : PartialFunction[Any, Unit] = {
    case IViewMsg(spiffy) => {
      render(spiffy)
    }
  }

  def render : PartialFunction[Any, Unit]
}
