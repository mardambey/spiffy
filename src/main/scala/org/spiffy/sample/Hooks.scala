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
    case h @ HookMsg(curHook, hooks, spiffy, ht) => {
      val randomInt = new Random().nextInt(10)
      if (randomInt > 6) {
	log.debug("[RandomErrorGeneratingHook] Running hook (will error) for " + spiffy.ctrl)
	notFound(spiffy.req, spiffy.res, spiffy.ctx)
      } else {
	log.debug("[RandomErrorGeneratingHook] Running hook (will not error) for " + spiffy.ctrl)
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
    case h @ HookMsg(curHook, hooks, spiffy, ht) => {

      spiffy.route match {
	case List("news", "see", id) => {
	  log.debug("[InternalRedirectingHook] Redirecting see -> view for " + id)
	  val newRoute = List("news", "view", id)
	  val hk = HookMsg(curHook, hooks, Spiffy(newRoute, spiffy.vmsg, spiffy.req, spiffy.res, spiffy.ctx, spiffy.ctrl), ht)
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
    case h @ HookMsg(curHook, hooks, spiffy, ht) => {
      log.debug("[LoggerHook] Running hook for " + spiffy.ctrl)
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

/**
 * Sample logging hook.
 */
class AnotherAfterHook extends Actor {

  def receive = {
    case h @ HookMsg(curHook, hooks, spiffy, ht) => {
      //log.debug("[AnotherAfterHook] Running hook for " + spiffy.ctrl)
      (forward orElse call) (h)
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Companion object that pools the hook.
 */
object AnotherAfterHook {
  val actor = pool(classOf[AnotherAfterHook], 100)
  def apply() = actor
}

/**
 * Sample logging hook.
 */
class AfterLoggerHook extends Actor {

  def receive = {
    case h @ HookMsg(curHook, hooks, spiffy, ht) => {
      //log.debug("[AfterLoggerHook] Running hook for " + spiffy.ctrl)
      (forward orElse call) (h)
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Companion object that pools the hook.
 */
object AfterLoggerHook {
  val actor = pool(classOf[AfterLoggerHook], 100)
  def apply() = actor
}
