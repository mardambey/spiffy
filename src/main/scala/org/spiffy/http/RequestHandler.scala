package org.spiffy.http

import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}

import scala.actors.Actor
import scala.actors.Actor._
import Console._

class RequestHandler extends Actor
{
	def act()
	{
		while (true)
		{
		  receive
		  {
		    // decide where to route this request
		    case List(request: SpiffyRequestWrapper, response: SpiffyResponseWrapper) => 
		    {		    			    	
		    	reply("done")
		    }
		    
		    case ignore => 
		    { 
		    	println("ignore! " + ignore) 
		    	reply("done") 
		    }
		  }
		}
	}
}
