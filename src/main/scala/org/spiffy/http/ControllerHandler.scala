package org.spiffy.http

import scala.collection.mutable.HashMap
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import akka.actor.Actor

import Console._

class ControllerHandler extends Actor
{
	def receive = {
      // decide where to route this request
      case List(c:Class[Actor], params:List[Any], List(request: SpiffyRequestWrapper, response: SpiffyResponseWrapper)) => {
        //println("controller handling!")
        //val r = controller(0) !? (params, request, response)
        //reply(None)
      }

      case ignore => {
        println("ignore! -->" + ignore)
        //reply("done")
      }
	}
}
