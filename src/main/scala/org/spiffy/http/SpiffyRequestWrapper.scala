package org.spiffy.http

import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}


class SpiffyRequestWrapper (request: HttpServletRequest) extends HttpServletRequestWrapper(request) 
{

}
