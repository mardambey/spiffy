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
      ChatController.chatters.foreach(client => {
	log.debug("Sending to " + client._2)
	client._2 ! Send(s.req.getParameter("data"))
	client._2 ! End()
      })

      ChatController.chatters.clear
      s.ctx.complete
  }

  def onListenConnect(s:Spiffy) {
    // register listener in case of errors
    s.ctx.addListener(ChatAsyncListener) 
    // add a client for this session        
    ChatController.chatters += (s.req.getSession.getId -> actorOf(new ChatClient(s)).start)
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

import collection.JavaConversions._
import collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import ChatController._

object ChatController {
  /**
   * Holds all chat clients by their session id.
   */
  val chatters:ConcurrentMap[String, ActorRef] = new ConcurrentHashMap[String, ActorRef]()
}

class ChatClient(s:Spiffy) extends Actor {
  def receive = {
    // send data to the client
    case Send(data:String) if (s.res != null)=> {
      log.debug("Sending msg: " + s.res + " -> " + data)
      s.res.getWriter.println(LongPollingController.whiteSpace + data)
      s.res.getWriter.flush     	
    }
    case End() if (s.res != null && s.ctx != null) => s.ctx.complete
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

/**
 * Event listener for the async context.
 */
object ChatAsyncListener extends AsyncListener {
  override def onComplete(e:AsyncEvent) { }
  override def onError(e:AsyncEvent) { log.error("AsyncError in " + e.getAsyncContext()) }
  override def onStartAsync(e:AsyncEvent) { }
  override def onTimeout(e:AsyncEvent) { log.error("AsyncTimeout in " + e.getAsyncContext()) }
}
