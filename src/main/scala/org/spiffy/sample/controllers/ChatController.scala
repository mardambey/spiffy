package org.spiffy.sample.controllers

import scala.util.matching.Regex
import scala.collection.mutable.ListBuffer
import javax.servlet.http._
import javax.servlet.{AsyncListener,AsyncEvent}
import Console._

import akka.actor.{Actor,ActorRef}
import akka.actor.Actor._

import org.spiffy.http.{ScalateViewHandler => view}
import org.spiffy.http._

/**
 * Basic chat controller.
 */
class ChatController extends LongPollingController {
  val BASE = "chat"

  /**
   * Called when new data arrives.
   */
  def onDataReceived(s:Spiffy) {
      LongPollingController.sessions.foreach(client => {
	log.debug("Sending to " + client._2)
	client._2 match {
	  case c:ActorRef => {
	    c ! Send(s.req.getParameter("d"))
	    c ! End()
	  }
	  case ignore => log.error("Ignored: " + ignore)
	}
      })      
  }

  def onCometConnect(s:Spiffy, data:ActorRef) {
    data match {
      case a:ActorRef => {
	log.debug("onCometConnect: updating spiffy on " + a + " to " + s)
	a !! s
      }
      case ignore => log.error("Ignored: " + ignore)
    }
  }

  def onHandshake(s:Spiffy) : Tuple2[String, ActorRef] = {
    val sessionKey = s.req.getSession.getId
    val actor = actorOf(new ChatClient(s))
    actor.start()
    ((sessionKey, actor))
  }

  override def receive = super.receive orElse localReceive

  def localReceive:PartialFunction[Any, Unit] = {
    /**
     * Brings up the chat page.
     */
   case s @ R("chat") => {           
      view() ! s.copy(vmsg = ViewMsg("chat.scaml", None.toMap[Any, Any]))      
    }  
    case ignore => log.error("Ignored: " + ignore)
  }
}

class ChatClient(var s:Spiffy) extends Actor {
  def receive = {
    // send data to the client
    case Send(data:String) if (s.res != null)=> {
      log.debug("Sending msg: " + s.res + " -> " + data)
      LongPollingController.send(s, "{data:\"" + data + "\"}");	
    }
    case End() if (s.res != null && s.ctx != null) => try { LongPollingController.end(s) } catch { case e:Exception => { log.error("AsyncContext already completed: " + s.ctx) } } 
    case spiffy:Spiffy => s = spiffy
    case _ if (s.res == null) => log.debug("Client left, not sending anything")
    case ignore => log.error("Ignored: " + ignore)
  }
}

/**
 * Signals the chat client to send data
 */
case class Send(data:String)

/**
 * Signals the chat client to end the response.
 */
case class End()


