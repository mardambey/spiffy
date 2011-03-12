package org.spiffy.http

import javax.servlet._
import http.{HttpServletResponseWrapper, HttpServletResponse, HttpServletRequest}


class SpiffyResponseWrapper (request: HttpServletResponse) extends HttpServletResponseWrapper(request) 
{

}
