package org.spiffy.http

import java.io.File

import org.fusesource.scalate._

import org.spiffy.config._
import org.spiffy.{WorkStealingSupervisedDispatcherService => pool}

import akka.actor.Actor

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
  val actor = pool(classOf[ScalateViewHandler], 100)
  def apply() = actor
}
