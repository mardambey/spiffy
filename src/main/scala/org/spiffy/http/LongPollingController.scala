package org.spiffy.http

import scala.collection.mutable.Queue
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

import net.liftweb.json._
import net.liftweb.json.JsonDSL._

import akka.actor.{Actor,ActorRef}
import akka.actor.Actor._
import LongPollingController._

import collection.JavaConversions._
import collection.mutable.{ConcurrentMap => CMap}
import java.util.concurrent.{ConcurrentHashMap => JCMap}
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
	case Some(key) if (sessions.contains(key)) => {
	  // check to see if we have any queued packets for this client, if so, 
	  // send them out and do not fire the onCometConnect
	  if (packetQ.isDefinedAt(key) && packetQ(key).size > 0) {	    
	    sendSeq(key, Some(s), packetQ(key))
	    packetQ(key).clear // TODO: this is not safe, we might lose packets
	    end(s)
	    log.debug("Dequeued and sent packets.")
	  } else {
	    // issue event to implementing class
	    onCometConnect(s, sessions.get(key).get)
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
	case Some(key) if (sessions.contains(key)) => {
	  // decode the packet
	  try {
	    val d = parse(s.req.getParameter("d"))
	    d match {
	      case JArray(List(JInt(id), JString(data))) => {
		// single packet
		onDataReceived(s, data)
		send(key, Some(s), "success")
		end(s)	       
	      }
	      case JArray(packets) => {
		log.debug("Received multiple packets: " + packets)
		// TODO: implement this		
	      }
	      case ignore =>
	    }
	  } catch {
	    case e:Exception => {
	      log.debug("Exception while decoding packets: " + e.getMessage())
	    }
	  }
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
      sessions += (sessionKey -> sessionData)
      packetQ += (sessionKey -> Queue[String]())
      packetIds += (sessionKey -> 1)
      
      // send the response and end the connection
      sendRaw(s, "\"" + sessionKey + "\"")
      end(s)      
    }
    
    /**
     * Close the session.
     * TODO: implement
     */
    case s @ R(BASE, "close") => {
    }

  }

  /**
   * Callback implementors use to be notified when a
   * new comet connection has been established.
   */
  def onCometConnect(s:Spiffy, data:ActorRef)

  /**
   * Callback implementors use to hand back a session
   * key and actor to couple it with.
   * TODO: the ActorRef restriction should go away and
   * it needs to be replaced with an Any.
   */
  def onHandshake(s:Spiffy):Tuple2[String, ActorRef]

  /**
   * Callback the child implements to be notified of the
   * arrival of new data.
   */
  def onDataReceived(s:Spiffy, msg:String)
  
  /**
   * Send headers to keep client hanging on and waiting for data.
   */
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

  /**
   * Maps sessions to actors (data associated with session)
   */
  val sessions:CMap[String, ActorRef] = new JCMap[String, ActorRef]()

  /**
   * Maps sessions to packet queues, used to hold packets that can not
   * be instantly delivered and are waiting for a comet conneciton.
   * TODO: this queue and the entire session need to time out if no
   * activity is received in a certain amount of time.
   */
  val packetQ:CMap[String, Queue[String]] = new JCMap[String, Queue[String]]()

  /**
   * Maps sessions to packet id counters.
   */
  val packetIds:CMap[String, Int] = new JCMap[String, Int]()

  /**
   * Ends the request by completing the async context
   */
  def end(s:Spiffy) = s.ctx.complete

  /**
   * Sends the given data as is wrapping it in a javascript
   * function who's name is provided in the initial request
   * under the parameter name "jsoncallback".
   */
  def sendRaw(s:Spiffy, data:String) {
    val resp = s.req.getParameter("jsoncallback") + "(" + data + ");"
    log.debug("Sending msg: " + resp)
    s.res.getWriter.println(resp)
    s.res.getWriter.flush
  }

  /**
   * Sends out data to the client, encodes it into a packet which
   * is a JSON array [id, data]. Increments the packet id counter.
   */
  def send(sessionKey:String, s:Option[Spiffy], data:String) {
    if (s.isDefined) {
      val id = packetIds(sessionKey)
      packetIds(sessionKey) = id + 1
      val resp = compact(render(JArray(List(id, data))))
      sendRaw(s.get, resp)
    } else {
      // if we're trying to send a packet and we have no connection 
      // we'll queue it so we can send it later
      packetQ(sessionKey) += data
      log.debug("Queueing packet: " + data + ", queue size = " + packetQ.size)
    } 
  }

  /**
   * Sends out data from the given sequence as a batch of packets in
   * JSON format.
   */
  def sendSeq(sessionKey:String, s:Option[Spiffy], packets:Seq[String]) {
    if (s.isDefined) {
      val id = packetIds(sessionKey)
      val data = JArray((id until id + packets.size zip packets).map(p => JArray(List(p._1, p._2))).toList)
      val json = compact(render(data))
      log.debug("Sending multiple packets: " + json)
      packetIds(sessionKey) += packets.size
      sendRaw(s.get, json)
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
