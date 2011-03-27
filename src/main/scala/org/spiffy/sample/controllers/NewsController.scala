package org.spiffy.sample.controllers

import scala.util.matching.Regex
import scala.collection.mutable.ListBuffer
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import Console._

import akka.actor.{Actor,ActorRef}
import akka.actor.Actor._

import org.spiffy.http.{ScalateViewHandler => view}
import org.spiffy.http._
import org.spiffy.validation._
import org.spiffy.sample.validation._
import org.spiffy.sample._

/**
 * Basic news controller that uses some of Spiffy's features.
 * 
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
class NewsController
  extends Actor
  with Validation
  with ValidationHelpers  
   {
  /**
   * Handles all incoming messages for this controller.
   * The receive method will be sent all the requests destined for
   * this controller. The message format is as follows:
   * 
   * ControllerMessage(List(), SpiffyRequestWrapper, SpiffyResponseWrapper, AsyncContext)
   *
   * The first List() contains all the parameters that the route configuration
   * for this URL captures, for example:
   *
   * new Regex("""^/(news)/(view)/(\d)$""") -> HelloWorldController(),
   *
   * The previous route will create:
   *
   * ControllerMessage(List("news", "view", "436"), req, res, ctx
   * 
   */
  def receive = {     
    // handles "news/view/$newsId/"
    case ControllerMsg(List("news", "view", newsId), req, res, ctx) => {
      // set the params that the view will render
      val params:Map[Any,Any] = Map("newsId" -> newsId, "actor" -> self.toString())

      // ask the view to render
      view() ! ViewMsg("newsView.scaml", params, req, res, ctx)
    }

    // handles "news/add/"
    case ControllerMsg(List("news", "save"), req, res, ctx) => {

      // run validation on the request
      var errors:Map[String, Set[String]] = validate (req) (
	"email" as email confirmedBy "email_confirm",
	"email_confirm" as email,
	"title" as (string, 32),
	"body" as string,
	"tag" as string optional
      ) match {
	case Some(List(errs,warns)) => { // Problem validating
	  errs
	}
	case None => { // No problems
	  None.toMap
	}
      }

      // validating all error messages in linked list
	val err = ListBuffer[String]()
      errors foreach { e => { e._2 foreach { err += _ }}}
      
      // just assign a fake id for now since we dont really add anything
      val newsId = "547"

      val params = Map[Any,Any]("newsId" -> newsId, "errors" -> err.toList)      
      view() ! ViewMsg("newsSave.scaml", params, req, res, ctx)
    }

    // handles main new page
    case ControllerMsg(List("news"), req, res, ctx) => {
      view() ! ViewMsg("news.scaml", None.toMap[Any, Any], req, res, ctx)
    }
   
    // shows form that adds news
    case ControllerMsg(List("news", "add"), req, res, ctx) => {
      view() ! ViewMsg("newsAdd.scaml", None.toMap[Any, Any], req, res, ctx)
    }

    // catch all
    case ignore =>
    {
      println(getClass() + ": ignoring the following: " + ignore)
    }
  }
}

/**
 * Companion object that defines before hooks to be ran.
 */
object NewsController extends BeforeHooks {

  /**
   * Array of before hooks.
   */
  val hooks = Array(LoggerHook(), InternalRedirectingHook())
  
  /**
   * Run the following hooks when asked.
   */
  val before = hooks
}
