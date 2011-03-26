package org.spiffy.http

import java.util.HashMap
import java.io.File
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import scala.collection.JavaConversions._

import org.fusesource.scalate._

import org.spiffy.config._

import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.ActorRegistry
import akka.actor.Actor
import akka.actor.SupervisorFactory
import akka.config.Supervision._

import Console._
import java.util.{Map => JMap}

object ScalateEngine {
  val engine = new TemplateEngine(List(new File(SpiffyConfig().WEBROOT +  "/WEB-INF/scalate/")))
  def apply():TemplateEngine = engine
}

/**
 * Responsible for rendering views. Accepts a map of parameters to replace in the view and
 * the name of the view to render.
 *
 * TODO: needs to be able to render multiple mime types.
 *
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
class ScalateViewHandler extends Actor
{
  /**
   * Set the dispatcher
   */
  self.dispatcher = ScalateViewHandler.dispatcher

  val engine = ScalateEngine()

  /**
   * Responsible for rendering the view.
   */
  def receive = {
    // render the view
    case ViewMsg(template, params, req, res, ctx) => {      
      val output = engine.layout(template , params.asInstanceOf[Map[String, Any]])
      if (res.getContentType == null) res.setContentType("text/html")
      res.getWriter.write(output)
      ctx.complete  
    }
		    
    // catch all, never happens
    case ignore => {
      println("view render ignored: " + ignore)
    }
  }
}

object ScalateViewHandler {

  // Count of actors that will balance the load
  val ACTORS_COUNT = 100

  /*
   * Initialization of the smart work stealing dispatcher that polls messages from
   * the mailbox of a busy actor and finds other actor in the pool that can process
   * the message.
   */
  val workStealingDispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pooled-dispatcher")
  var dispatcher = workStealingDispatcher
  .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
  .setCorePoolSize(ACTORS_COUNT)
  .buildThreadPool

  /*
   * Creates list of actors the will be supervized
   */
  def createListOfSupervizedActors(poolSize: Int): List[Supervise] = {
    (1 to poolSize toList).foldRight(List[Supervise]()) {
      (i, list) => Supervise(Actor.actorOf( { new ScalateViewHandler() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = ActorRegistry.actorsFor[ScalateViewHandler](classOf[ScalateViewHandler]).head

  // supervisor.stop
  // supervisor.shutdown

}
