package org.spiffy.sample

import scala.util.Random
import akka.actor.Actor

import org.spiffy.{WorkStealingSupervisedDispatcherService => pool}
import org.spiffy.http._
import org.spiffy.http.HookHelpers._
import org.spiffy.Helpers._

/**
 * Sample hook that generates a 404 randomly.
 */
class RandomErrorGeneratingHook extends Actor {
  def receive = {
    case h@ HookMsg(curHook, hooks, ctrl, msg) => {
      val randomInt = new Random().nextInt(10)
      if (randomInt > 6) {
	log.debug("[RandomErrorGeneratingHook] Running hook (will error) for " + ctrl)
	notFound(msg.req, msg.res, msg.ctx)
      } else {
	log.debug("[RandomErrorGeneratingHook] Running hook (will not error) for " + ctrl)
	(forward orElse call) (h)
      }
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Companion object that pools the hook.
 */
object RandomErrorGeneratingHook {
  val actor = pool(classOf[RandomErrorGeneratingHook], 100)
  def apply() = actor
}

/**
 * Sample hook that redirects the request from "see" to "view".
 */
class InternalRedirectingHook extends Actor {
  def receive = {
    case h @ HookMsg(curHook, hooks, ctrl, msg @ ControllerMsg(route, req, res, ctx)) => {

      route match {
	case List("news", "see", id) => {
	  log.debug("[InternalRedirectingHook] Redirecting see -> view for " + id)
	  val newRoute = List("news", "view", id)
	  val hk = HookMsg(curHook, hooks, ctrl, ControllerMsg(newRoute, req, res, ctx))
	  (forward orElse call) (hk)
	}
	case _ => {
	  (forward orElse call) (h)
	}
      }
    }

    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Companion object that pools the hook.
 */
object InternalRedirectingHook {
  val actor = pool(classOf[InternalRedirectingHook], 100)
  def apply() = actor
}

/**
 * Sample logging hook.
 */
class LoggerHook extends Actor {

  def receive = {
    case h @ HookMsg(curHook, hooks, ctrl, msg) => {
      log.debug("[LoggerHook] Running hook for " + ctrl)     
      (forward orElse call) (h)
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Companion object that pools the hook.
 */
object LoggerHook {
  val actor = pool(classOf[LoggerHook], 100)
  def apply() = actor
}
