package org.spiffy.filter

import javax.servlet._
import annotation.WebFilter
import http.{HttpServletResponse, HttpServletRequest}
import java.util.logging.Logger

import akka.actor.Actor

import org.spiffy.http._
import org.spiffy.config.SpiffyConfig

/**
 * Spiffy supports async contexts and "takes over everything in its path".
 */
@WebFilter (asyncSupported=true, urlPatterns=Array("/*"), filterName="spiffy")
class SpiffyFilter extends Filter {

  val router = SpiffyConfig.ROUTER_ACTOR

  @throws(classOf[ServletException])
  def init(filterConfig: FilterConfig) : Unit = {
    Logger.global.info("> Spiffy'ing up! : init()");
  }

  @Override
  def doFilter(req:ServletRequest, res:ServletResponse, chain:FilterChain) {
    (req,res) match {
      case (req: HttpServletRequest, res: HttpServletResponse) => {

	// go into asynchronous mode with a timeout
        val asyncCtx = req.startAsync
        asyncCtx.setTimeout(SpiffyConfig.ASYNC_TIMEOUT)

        // route this request
        router ! wrap(req, res, asyncCtx)
      }
    }
  }

  @Override
  def destroy() {

  }

  def wrap(request: HttpServletRequest, response:HttpServletResponse, ctx:AsyncContext) : ReqResCtx = new ReqResCtx(new SpiffyRequestWrapper(request), new SpiffyResponseWrapper(response), ctx)
}
