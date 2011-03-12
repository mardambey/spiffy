package org.spiffy.http

import java.util.HashMap
import freemarker.template.Template
import freemarker.template.DefaultObjectWrapper
import java.io.File
import freemarker.template.Configuration
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import scala.collection.JavaConversions._
import akka.actor.Actor
import org.spiffy.actor._
import org.spiffy.config._
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.ActorRegistry
import akka.actor.SupervisorFactory
import akka.config.Supervision._
import Console._
import java.util.{Map => JMap}

/**
 * Responsible for rendering views. Accepts a map of parameters to replace in the view and
 * the name of the view to render.
 *
 * TODO: needs to be able to render multiple mime types.
 *
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
class ViewHandler extends Actor
{
  /**
   * Set the dispatcher
   */
  self.dispatcher = ViewHandler.dispatcher

  val freemarker = new Configuration()
  // initialize Freemarker
  freemarker.setDirectoryForTemplateLoading(new File(SpiffyConfig.WEBROOT + "/WEB-INF/ftl"))
  freemarker.setObjectWrapper(new DefaultObjectWrapper())

  /**
   * Responsible for rendering the view.
   */
  def receive = {
    // render the view
    case List(template:String, map:Map[Any, Any], List(req: SpiffyRequestWrapper, res: SpiffyResponseWrapper, ctx:AsyncContext)) => {
      
      try {
	val temp = freemarker.getTemplate(template + ".ftl");
	val h = new HashMap[Any, Any]
	map foreach { case(k,v) => { h.put(k, v) } }
	if (res.getContentType == null) res.setContentType("text/html")
	temp.process(h, res.getWriter)
      } catch {
	case e:Exception => {
	  res.setContentType("text/html")
	  res.getWriter.write("<h1>Freemarker error</h1>\n<pre><code>" + e.getMessage()  + "\n\n" + e.getStackTraceString + "</code></pre>")
	}
      }

      ctx.complete
    }
		    
    // catch all, never happens
    case ignore => {
      println("view render ignored: " + ignore)
    }
  }
}

object ViewHandler {

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
      (i, list) => Supervise(Actor.actorOf( { new ViewHandler() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = ActorRegistry.actorsFor[ViewHandler](classOf[ViewHandler]).head

  // supervisor.stop
  // supervisor.shutdown

}
