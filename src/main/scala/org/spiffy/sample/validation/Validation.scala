package org.spiffy.sample.validation

import org.spiffy.http.SpiffyRequestWrapper
import org.spiffy.validation._

object uniqNickName extends SpiffyValidator {
  def apply(args:SpiffyValidatorArgs) : Option[String] = {    
    try { 
      // TODO: implement this, look up nick name in the database
      val nick = args.req.getParameter(args.field)
      None
    } catch {
      case e:Exception => {
	Some(args.field + ": unknown error")
      }
    }
  }
}
