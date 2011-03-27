package org.spiffy.http

import java.util.HashMap
import freemarker.template.Template
import freemarker.template.DefaultObjectWrapper
import freemarker.template.Configuration
import java.io.File
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import scala.collection.JavaConversions._

import org.spiffy.{WorkStealingSupervisedDispatcherService => pool}

import org.spiffy.config._

import akka.actor.Actor

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
class FreemarkerViewHandler extends Actor
{
  val freemarker = new Configuration()
  // initialize Freemarker
  freemarker.setDirectoryForTemplateLoading(new File(SpiffyConfig().WEBROOT + "/WEB-INF/ftl"))
  freemarker.setObjectWrapper(new DefaultObjectWrapper())

  /**
   * Responsible for rendering the view.
   */
  def receive = {
    // render the view
    case ViewMsg(template, map, req, res, ctx) => {      
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

object FreemarkerViewHandler {
  val actor = pool(classOf[FreemarkerViewHandler], 100)
  def apply() = actor
}
