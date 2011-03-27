package org.spiffy.http

import javax.servlet.AsyncContext
import akka.actor.ActorRef

/**
 * Message that is sent to controllers.
 */
case class ControllerMsg(route:List[Any], req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)

/**
 * Message that is sent to views.
 */
case class ViewMsg(template:String, params:Map[Any, Any], req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)

/**
 * Message that is sent to hooks.
 */
case class HookMsg(curHook:Int, hooks:Array[ActorRef], ctrl:ActorRef, msg:ControllerMsg)
