package org.spiffy.http

import javax.servlet.AsyncContext
import akka.actor.{Actor,ActorRef}

case class Spiffy(route:List[Any], vmsg: ViewMsg, req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext, ctrl:ActorRef)

/**
 * Message that is sent to views.
 */
case class ViewMsg(template:String, params:Map[Any, Any])

/**
 * If a view handler is sent this message it will not
 * attempt to check for hooks.
 */
case class IViewMsg(s:Spiffy)

object HookType extends Enumeration {
      type HookType = Value
      val Before, After = Value
}

import HookType._

/**
 * Message that is sent to hooks.
 */
case class HookMsg(curHook:Int, hooks:Array[ActorRef], spiffy:Spiffy, h:HookType)
