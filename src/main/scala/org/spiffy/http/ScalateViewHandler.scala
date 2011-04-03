package org.spiffy.http

import java.io.File

import org.fusesource.scalate._

import org.spiffy.config._
import org.spiffy.{WorkStealingSupervisedDispatcherService => pool}
import org.spiffy.Helpers._
import org.spiffy.http.HookType._

import akka.actor.{Actor,ActorRef}

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
class ScalateViewHandler extends  AfterHookEnabled
{  
  val engine = ScalateEngine()

  def render = {
    // render the view
    case Spiffy(route, ViewMsg(template, params), req, res, ctx, ctrl) => {     
      val output = engine.layout(template , params.asInstanceOf[Map[String, Any]])
      if (res.getContentType == null) res.setContentType("text/html")
      res.getWriter.write(output)
      res.getWriter.flush
      ctx.complete  
      true
    }
		    
    // catch all, never happens
    case ignore => {
      println("view render ignored: " + ignore)
      true
    }
  }
}

object ScalateViewHandler {
  val actor = pool(classOf[ScalateViewHandler], 100)
  def apply() = actor
}
