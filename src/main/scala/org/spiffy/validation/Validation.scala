package org.spiffy.validation

import scala.util.matching.Regex
import org.spiffy.http.SpiffyRequestWrapper

trait Validation {

  implicit def string2SpiffyValidator(s:String) : SpiffyValidatorStruct = new SpiffyValidatorStruct(s)

  def validate(req:SpiffyRequestWrapper)(validators:SpiffyValidatorStruct*) : Option[List[Map[String, Set[String]]]]= {
    var errors = Map[String, Set[String]]()
    var warnings = Map[String, Set[String]]()

    for (v <- validators.elements) {
      // TODO: clean this up and make the error message changable
      val err = v.validate(req)

      if (err != None) {
	if (v.opt) { // optional, add as warning
	  var strings = warnings.getOrElse(v.keyName, Set[String]())
	  strings += err.get
	  warnings += v.keyName -> strings
	} else { // error, add to errors
	  var strings = errors.getOrElse(v.keyName, Set[String]())
	  strings += err.get
	  errors += v.keyName -> strings
	}
      }

      try {
	if (v.confirm != None.toString && !req.getParameter(v.confirm).equals(req.getParameter(v.keyName))) {
	  var strings = errors.getOrElse(v.keyName, Set[String]())
	  strings += "Confirmation error."
	  errors += v.keyName -> strings
	}
      } catch {
	case e:Exception => //println(e.getMessage())
      }
    }

    if (errors.size > 0 || warnings.size > 0) return Some(List(errors, warnings))
    else None
  }

  class SpiffyValidatorStruct(val keyName:String) {
    var validator:SpiffyValidator = _
    var args:Array[Any] = _
    var opt = false
    var confirm:String = _

    def as(v:SpiffyValidator, va:Any*) : SpiffyValidatorStruct = {
      validator = v
      args = va.toArray
      this
    }

    def optional() : SpiffyValidatorStruct = {
      opt = true
      this
    }

    def confirmedBy(field:String) : SpiffyValidatorStruct = {
      confirm = field
      this
    }

    def validate(req:SpiffyRequestWrapper) : Option[String] = {
      // run validator
      validator(SpiffyValidatorArgs(keyName, req, args))
    }
  }
}

class SpiffyValidatorArgs(val field:String, val req:SpiffyRequestWrapper, val args:Array[Any])
object SpiffyValidatorArgs { def apply(field:String, req:SpiffyRequestWrapper, args:Array[Any]) = new SpiffyValidatorArgs(field, req, args) }

trait SpiffyValidator {
  def apply(args:SpiffyValidatorArgs) : Option[String]
}

object string extends SpiffyValidator {
  def apply(args:SpiffyValidatorArgs) : Option[String] = {
    try {
      if (args.req.getParameter(args.field).length > 0) None
      else Some(args.field + ":error!")
    } catch {
      case e:Exception => {
	Some(args.field + ": unknown error")
      }
    }
  }
}

object email extends  SpiffyValidator {
  val emailRegex = """([\w\d\-\_]+)(\+\d+)?@([\w\d\-\.]+)""".r
  def apply(args:SpiffyValidatorArgs) : Option[String] = {
    try {
      if (emailRegex.pattern.matcher(args.req.getParameter(args.field)).matches) {
	None
      } else {
	Some(args.field + "(" + args.req.getParameter(args.field) + "): error!")
      }
    } catch {
      case e:Exception => {
	Some(args.field + ": unknown error")
      }
    }
  }
}
