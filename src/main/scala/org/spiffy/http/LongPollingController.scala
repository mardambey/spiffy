package org.spiffy.http

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

import akka.actor.{Actor,ActorRef}
import akka.actor.Actor._

/**
 * Controller that implements basic listening and sending functionality
 * over long polling.
 * 
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
trait LongPollingController extends Actor
{
  val httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
  val now = { httpDateFormat.format(new Date) }
  val BASE:String // base name the child controller will be using: "/chat/"
  def receive = {        
    /**
     * Handles "/base/listen". Clients request this url and block until data
     * is sent over to them at which point they will process it and connect
     * again to this url.
     */
    case s @ R(BASE, "listen") => {     
      onListenConnect(s)     
      // send out headers to keep client hanging on
      headers(s)
      log.debug("Added a new client: " + s.ctx + " - " + s.req.getSession)
    }

    /**
     * Handles "/chat/send". Clients post to this url when they
     * need to send data to the server.
     */
    case s @ R(BASE, "send") => {
      log.debug("Data being sent: " + s.req.getParameter("data"))
      onDataReceived(s)
    }    
  }

  def onListenConnect(s:Spiffy)

  /**
   * Callback the child implements to be notified of the
   * arrival of new data.
   */
  def onDataReceived(s:Spiffy)

  def send(s:Spiffy, data:String) {
    s.res.getWriter.println(data)     
    s.res.getWriter.flush
  }

  def headers(s:Spiffy) {
    s.res.setHeader("Expires", "Mon, 26 Jul 1997 05:00:00 GMT");
    s.res.setHeader("'Last-Modified", now);
    s.res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    s.res.setHeader("Cache-Control", "post-check=0, pre-check=0");
    s.res.setHeader("Pragma", "no-cache");
    s.res.setContentType("text/plain")
  }
}

object LongPollingController {
  final val whiteSpace = " " * 1024
}
