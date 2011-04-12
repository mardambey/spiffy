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
 * TODO:
 * Send null packet on session end.
 * Implement persistent variables.
 * Implement per session variables.
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
      val sessionKey = Option(s.req.getParameter(SESSION_KEY))
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
      log.debug("Data being sent: " + s.req.getParameter(DATA))
      // verify session is valid
      val sessionKey = Option(s.req.getParameter(SESSION_KEY))
      sessionKey match {
	case Some(key) if (sessions.contains(key)) => {
	  // decode the packet
	  try {
	    val d = parse(s.req.getParameter(DATA))
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
   * The DURATION variable signifies how long the server
   * should leave a Comet request open before completing
   * the response. A value of "0" will cause a response to
   * always be sent immediately after a request is received,
   * thus setting the connection mode to polling. Values of
   * DURATION > 0 are used in conjunction with streaming
   * and long polling, and will typically range from 10-45
   * seconds. A DURATION value of 0 will override IS_STREAMING
   * with the value 0, forcing the connection into polling mode.
   */
  val DURATION = "du"

  /**
   * The IS_STREAMING variable signifies to the server
   * if the Comet HTTP response should be completed
   * after a single batch of packets. A value of 1 for
   * IS_STREAMING means that the server will never
   * complete the HTTP response due to a batch of
   * packets, only when the DURATION has expired.
   * This is the streaming mode. Any other value for
   * IS_STREAMING will cause the server to always
   * complete the HTTP response after sending a
   * batch of packets. In this case, the connection
   * will be in long polling mode.
   */
  val IS_STREAMING = "is"

  /**
   * The INTERVAL variable signifies the idle interval (time
   * since the connection opened or the last packet batch
   * was sent) after which an empty batch of packets will be
   * sent. The INTERVAL variable will be ignored unless the
   * value of IS_STREAMING is 1. The purpose of the INTERVAL
   * variable is to keep intermediaries from closing a streaming
   * connection due to inactivity.
   */
  val INTERVAL = "i"

  /**
   * The PREBUFFER_SIZE variable is parsed as an integer and
   * determines the number of empty bytes (U+0020) to send at
   * the start of the body of each HTTP Comet response. It is
   * ignored unless the value of IS_STREAMING is 1. The purpose
   * of the PREBUFFER_SIZE is to meet minimum buffering
   * conditions that cause some intermediaries to delay delivery
   * of initial events in a streaming connection. Obvious example
   * include the IE and Webkit network stacks.
   */
  val PREBUFFER_SIZE = "ps"

  /**
   * The PREAMBLE variable indicates a default string that will be
   * sent at the start of each Comet HTTP response body. These
   * bytes will be sent after any empty bytes resulting from a
   * PREBUFFER_SIZE > 0. The purpose of the PREAMBLE is to
   * enable various Comet transports, including iframe streaming
   * and ActiveX('htmlfile') streaming.
   */
  val PREAMBLE = "p"

  /**
   * The BATCH_PREFIX variable indicates a default string that will
   * be sent immediately before each batch of packets. The purpose of
   * the BATCH_PREFIX variable is to enable various Comet transports,
   * including jsonp polling/long polling, various forms of script-tag
   * streaming, and sse.
   */
  val BATCH_PREFIX = "bp"

  /**
   * The BATCH_SUFFIX variable indicates a default string that will
   * be sent immediately after each batch of packets. The purpose
   * of the BATCH_SUFFIX variable is to enable various Comet
   * transports, including sse and various forms of script-tag streaming.
   */
  val BATCH_SUFFIX = "bs"
  
  /**
   * The GZIP_OK variable indicates that it is acceptable to use
   * gzip-encoded responses to any request. No server is required
   * to support gzipping. This variable is used instead of the
   * Accept-Encoding header because clients may not always have
   * control of the header. Furthermore, even if a browser purports
   * to support gzip, some streaming transports may be buffered i
   * ncorrectly when gzip is used. If there there is an existing HTTP
   * Comet request that hasn't been completed when another request
   * changes the value of GZIP_OK, that request must immediately
   * complete its response. (See 3.3.4.4 Completing the Response.)
   */
  val GZIP_OK = "g"

  /**
   * If the SSE variable is 1, then SSE_ID is: id: %(LAST_MSG_ID)\r\n
   * where LAST_MSG_ID is the packet sequence id of the last
   * message in the batch. Otherwise SSE_ID is an empty string.
   * The purpose of the SSE variable is to enable the server-sent
   * events transports as defined by the html5 specification.
   * Specifically, this variable causes the browser's event-source
   * tag to send a correct Last-Event-Id when reconnecting.
   */
  val SSE = "se"

  /**
   * The CONTENT_TYPE variable is a string that represents the
   * value of the Content-Type header that will be used in all
   * HTTP response (both for HTTP Comet response and ordinary
   * responses.) It is used to enable multiple variants of HTML5
   * and Opera server-sent events, IE XML streaming, and
   * reduce the required value of PREBUFFER_SIZE in Webkit.
   * The default value of CONTENT-TYPE is text/html.
   */
  val CONTENT_TYPE = "ct"

  /**
   * The REQUEST_PREFIX indicates the default string that will
   * be sent in the body of a response immediately before the
   * result of that response. It is used to specify a callback for
   * jsonp-style script-tag requests. Once these variables are
   * set, they persist for all responses to /handshake and /send
   * requests, until explicitly altered.
   */
  val REQUEST_PREFIX = "rp"

  /**
   * The REQUEST_SUFFIX indicates the default string that is sent
   * in the body of a response immediately following the result
   * of that response.
   */
  val REQUEST_SUFFIX = "rs"

  /**
   The SESSION_KEY is first provided in the handshake by the server,
   * and must be sent by the client in all subsequent requests. Any
   * non-handshake request missing a SESSION_KEY is invalid.
   */
  val SESSION_KEY = "s"

  /**
   * The ACK_ID variable represents the highest packet sequence id that the
   * client previously received. The value of an ACK_ID must be an integer.
   * Any request following the handshake may have an ACK_ID. The ACK_ID
   * is not persisted.
   */
  val ACK_ID = "a"

  /**
   * The DATA variable represents a client -> server payload of data encoded as a
   * batch of packets. It is used with requests to /send and /handshake (in order
   * to specify the value of the handshake object), and does not persist.
   * */
  val DATA = "d"

  /**
   * The NO_CACHE variable is ignored by the server. It can be used by the client to
   * keep a browser or proxy cache from caching the response to a comet, handshake,
   * or send request. If necessary, the client should base the value of NO_CACHE on
   * the javascript Date object. NO_CACHE is not persisted.
   */
  val NO_CACHE = "n"

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
