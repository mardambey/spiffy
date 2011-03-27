package org.spiffy.sample

import scala.util.Random
import akka.actor.Actor

import org.spiffy.http._
import org.spiffy.Helpers._

class RandomErrorGeneratingHook extends Actor {
  def receive = {
    case HookMsg(ctrl, msg) => {
      val randomInt = new Random().nextInt(10)
      if (randomInt > 6) {
	log.debug("[RandomErrorGeneratingHook] Running hook (will error) for " + ctrl)
	notFound(msg.req, msg.res, msg.ctx)
      } else {
	log.debug("[RandomErrorGeneratingHook] Running hook (will not error) for " + ctrl)
	ctrl ! msg
      }
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}

class InternalRedirectingHook extends Actor {
  def receive = {
    case HookMsg(ctrl, msg @ ControllerMsg(route, req, res, ctx)) => {
      route match {
	case List("news", "see", id) => {
	  log.debug("[InternalRedirectingHook] Redirecting see -> view for " + id)
	  val newRoute = List("news", "view", id)
	  ctrl ! ControllerMsg(newRoute, req, res, ctx)
	}
	case _ => ctrl ! msg
      }
    }

    case ignore => log.error("Ignored: " + ignore)
  }
}

class LoggerHook extends Actor {
  def receive = {
    case HookMsg(ctrl, msg) => {
      log.debug("[LoggerHook] Running hook for " + ctrl)
      ctrl ! msg
    }
    case ignore => log.error("Ignored: " + ignore)
  }
}
