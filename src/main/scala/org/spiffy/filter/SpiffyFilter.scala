package org.spiffy.filter

import javax.servlet._
import annotation.WebFilter
import akka.actor.Actor
import http.{HttpServletResponse, HttpServletRequest}
import org.spiffy.http._

case class RequestResponseCtx(req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)

@WebFilter (asyncSupported=true, urlPatterns=Array("/*"), filterName="spiffy")
class SpiffyFilter extends Object with Filter {

  val router = Router()

  @throws(classOf[ServletException])
  def init(filterConfig: FilterConfig) : Unit =
  {
    println("> Spiffy'ing up! : init()");    
  }

  @Override
  def doFilter(req:ServletRequest, res:ServletResponse, chain:FilterChain) {
    (req,res) match {
      case (req: HttpServletRequest, res: HttpServletResponse) => {

      // go into asynchronous mode with a 1 second timeout
        val asyncCtx = req.startAsync
        asyncCtx.setTimeout(1)

        // route this request
        router ! wrap(req, res, asyncCtx)
      }
    }
  }

  @Override
  def destroy() {

  }

  def wrap(request: HttpServletRequest, response:HttpServletResponse, ctx:AsyncContext) : RequestResponseCtx = new RequestResponseCtx(new SpiffyRequestWrapper(request), new SpiffyResponseWrapper(response), ctx)
}









