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
    val actor = actorOf(new ChatClient(Some(s)))
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

/**
 * A chat client holds a Spiffy variable when it has access
 * to a comet connection it can use to send data to its
 * corresponding web client.
 */
class ChatClient(var s:Option[Spiffy]) extends Actor {
  def receive = {
    // send data to the client wrapped in json format 
    // with key named "data"
    case Send(data:String) if (s.get != None)=> {
      log.debug("Sending msg: " + s.get.res + " -> " + data)
      LongPollingController.send(s.get, "{data:\"" + data + "\"}");
    }

    // end the connection, remove the spiffy object and wait 
    // for a new one
    case End() if (s.get != None) => {
      try { 
	val spiffy = s.get
	s = None
	LongPollingController.end(spiffy) 
      } catch { 
	case e:Exception => { 
	  log.error("AsyncContext already completed: " + e.getMessage)
	} 
      } 
    }

    // a new spiffy object means we are now holding a 
    // new connection
    case spiffy:Spiffy => s = Some(spiffy)
  
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


