package org.spiffy.http

import scala.collection.mutable.Queue
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

import akka.actor.{Actor,ActorRef}
import akka.actor.Actor._
import LongPollingController._

import collection.JavaConversions._
import collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.{AsyncListener,AsyncEvent}

/**
 * Controller that implements basic listening and sending functionality
 * over long polling.
 * 
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
trait LongPollingController extends Actor
{
  /**
   * date format used in http headers
   */
  val httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

  /**   
   * current timestamp
   */
  val now = { httpDateFormat.format(new Date) }
  /**
   * base name the child controller will be using: "/chat/"
   */
  val BASE:String

  /**
   * Handles incoming requests.
   */
  def receive = {            
     // Handles "/base/comet". Clients request this url and block until data
     // is sent over to them at which point they will process it and connect
     // again to this url.     
    case s @ R(BASE, "comet") => {
      // verify session is valid
      val sessionKey = Option(s.req.getParameter("s"))
      sessionKey match {
	case Some(key) if (LongPollingController.sessions.contains(key)) => {

	  // check to see if we have any queued packets for this client, if so, 
	  // send them out and do not fire the onCometConnect
	  // TODO: when we implement multiple packets we can fire them all 
	  // out at once, or multiple ones, then also call onCometConnect if 
	  // the queue size is empty
	  if (packetQ.isDefinedAt(key) && packetQ(key).size > 0) {
	    val data = packetQ(key).dequeue
	    LongPollingController.send(key, Some(s), data) // packet already jsonified
	    LongPollingController.end(s)
	    log.debug("Dequeued and sent packet: " + data + ", queue size = " + packetQ(key).size)
	  } else {
	    onCometConnect(s, LongPollingController.sessions.get(key).get);
	    // listen to events, if errors occur, clean up
	    s.ctx.addListener(LongPollingAsyncListener)
	    // send enough headers to keep the client connected and waiting
	    headers(s)
	    log.debug("Client connected to comet: " + s.ctx + " - " + s.req.getSession)	  
	  }
	}
	
	 // No key
	case ignore => {
	  log.debug("Client did not provide sessionKey or sessionKey not registered, handshake first: " + s)
	  s.ctx.complete
	}	
      }      
    }
    
    // Handles "/base/send" by making sure there is a valid session 
    // then calling the implementor's onDataReceived callback
    case s @ R(BASE, "send") => {
      log.debug("Data being sent: " + s.req.getParameter("d"))
      // verify session is valid
      val sessionKey = Option(s.req.getParameter("s"))
      sessionKey match {
	case Some(key) if (LongPollingController.sessions.contains(key)) => {
	  onDataReceived(s)
	  LongPollingController.send(key, Some(s), "{data:\"success\"}")
	  LongPollingController.end(s)
	}
	case ignore => {
	  log.debug("Client did not provide sessionKey or sessionKey not registered, handshake first: " + s.ctx + " - " + s.req.getSession)
	  s.ctx.complete
	}
      }
    }

    /**
     * The hand shake process gives back the client a session key that has
     * to be used to make future interactions.
     */
    case s @ R(BASE, "handshake") => {
      // ask for a session key from our implementor, also store the data 
      // that he wishes to associate with this session key
      val ((sessionKey, sessionData)) = onHandshake(s);
      log.debug("Added new handshake: " + sessionKey + " -> " + sessionData)
      // register this session
      LongPollingController.sessions += (sessionKey -> sessionData)
      LongPollingController.packetQ += (sessionKey -> Queue[String]())
      val resp = "{session:\"" + sessionKey + "\"}"      
      // send the response and end the connection
      send(sessionKey, Some(s), resp)
      end(s)      
    }
    
    case s @ R(BASE, "close") => {
    }

  }

  def onCometConnect(s:Spiffy, data:ActorRef)

  def onHandshake(s:Spiffy):Tuple2[String, ActorRef]

  /**
   * Callback the child implements to be notified of the
   * arrival of new data.
   */
  def onDataReceived(s:Spiffy)
  
  def headers(s:Spiffy) {
    s.res.setHeader("Expires", "Mon, 26 Jul 1997 05:00:00 GMT")
    s.res.setHeader("'Last-Modified", now)
    s.res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate")
    s.res.setHeader("Cache-Control", "post-check=0, pre-check=0")
    s.res.setHeader("Pragma", "no-cache")
    s.res.setContentType("text/javascript")
  }
}

object LongPollingController {

  val sessions:ConcurrentMap[String, ActorRef] = new ConcurrentHashMap[String, ActorRef]()
  val packetQ:ConcurrentMap[String, Queue[String]] = new ConcurrentHashMap[String, Queue[String]]()

  final val whiteSpace = " " * 1024

  def end(s:Spiffy) = s.ctx.complete

  def send(sessionKey:String, s:Option[Spiffy], data:String) {

    if (s.isDefined) {
      val resp = s.get.req.getParameter("jsoncallback") + "(" + data + ");"
      log.debug("Sending msg: " + resp)
      s.get.res.getWriter.println(resp)
      s.get.res.getWriter.flush
    } else {
      // if we're trying to send a packet and we have no connection 
      // we'll queue it so we can send it later
      packetQ(sessionKey) += data
      log.debug("Queueing packet: " + data + ", queue size = " + packetQ.size)
    } 
  }
}

/**
 * Event listener for the async context.
 */
object LongPollingAsyncListener extends AsyncListener {
  override def onComplete(e:AsyncEvent) { }
  override def onError(e:AsyncEvent) { log.error("AsyncError in " + e.getAsyncContext()) }
  override def onStartAsync(e:AsyncEvent) { }
  override def onTimeout(e:AsyncEvent) { log.error("AsyncTimeout in " + e.getAsyncContext()) }
}
