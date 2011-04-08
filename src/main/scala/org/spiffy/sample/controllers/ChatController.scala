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
  // LongPollingController api, this is where the application urls point
  val BASE = "chat"

  /**
   * Called when new data arrives.
   */
  def onDataReceived(s:Spiffy, msg:String) {
      LongPollingController.sessions.foreach(client => {
	log.debug("Sending to " + client._2)
	client._2 match {
	  case c:ActorRef => {
	    c ! Send(msg)
	    c ! End()
	  }
	  case ignore => log.error("Ignored: " + ignore)
	}
      })      
  }

  /**
   * Called when we receive a new comet connection
   */
  def onCometConnect(s:Spiffy, data:ActorRef) {
    data match {
      case a:ActorRef => {
	log.debug("onCometConnect: updating spiffy on " + a + " to " + s)
	a ! s
      }
      case ignore => log.error("Ignored: " + ignore)
    }
  }

  /**
   * Called when a new client is handshaking so we can
   * hand them back a session id and our associated data.
   */
  def onHandshake(s:Spiffy) : Tuple2[String, ActorRef] = {
    val sessionKey = s.req.getSession.getId
    val actor = actorOf(new ChatClient(s, sessionKey))
    actor.start()
    ((sessionKey, actor))
  }

  override def receive = super.receive orElse localReceive

  def localReceive:PartialFunction[Any, Unit] = {    
    // Brings up the chat page.    
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
class ChatClient(spiffy:Spiffy, sessionKey:String) extends Actor {

  var s:Option[Spiffy] = Some(spiffy)

  def receive = {
    // send data to the client wrapped in json format 
    // with key named "data"
    case Send(data:String) => {
      log.debug("Sending msg: " + data)
      LongPollingController.send(sessionKey, s, data);
    }

    // end the connection, remove the spiffy object and wait 
    // for a new one
    case End() if (s.isDefined) => {
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
    // new comet connection.
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


