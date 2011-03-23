package org.spiffy.http

import akka.actor.Actor

/**
 * This is the default error handler. It has basic error reporting
 * and lumps almost everything else as an internal server error.
 *
 * @author Hisham Mardam-Bey <hisham.marambey@gmail.com>
 */
class HttpErrorHandler extends Actor {
  def receive = {
    case (errno:Int, ReqResCtx(req,res,ctx)) => {
      errno match {

	case 404 => {
	  res.getWriter.write("404 - not found")
	  res.setStatus(404)
	  ctx.complete
	}

	case _ => {
	  res.getWriter.write("500 - internal server error")
	  res.setStatus(500)
	  ctx.complete
	}

      }
    }
  }
}
