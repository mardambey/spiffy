package org.spiffy.sample.controllers

import scala.util.matching.Regex
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import Console._

import akka.actor.Actor
import akka.actor.Actor._
import akka.actor.ActorRegistry
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.SupervisorFactory
import akka.config.Supervision._

import org.spiffy.http.{FreemarkerViewHandler => view}
import org.spiffy.http._
import org.spiffy.validation._
import org.spiffy.sample.validation._

import java.util.LinkedList

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
   * Set the dispatcher.
   * This does not to be done if your actor does not use a special
   * dispatcher. This controllers shows how a work stealing dispatcher
   * can be used to distribute load across several actors.
   */
  self.dispatcher = NewsController.dispatcher

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
      val params:Map[Any,Any] = Map("newsId" -> newsId)

      // ask the view to render
      view() ! ViewMsg("newsView", params, req, res, ctx)
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
      val err = errors2LinkedList(errors)
      
      // just assign a fake id for now since we dont really add anything
      val newsId = 547

      val params = Map[Any,Any]("newsId" -> newsId, "errors" -> err.getOrElse(null))
      // TODO: check if the given profile information is valid      
      view() ! ViewMsg("newsSave", params, req, res, ctx)
    }

    // handles main new page
    case ControllerMsg(List("news"), req, res, ctx) => {
      view() ! ViewMsg("news", None.toMap[Any, Any], req, res, ctx)
    }
   
    // shows form that adds news
    case ControllerMsg(List("news", "add"), req, res, ctx) => {
      view() ! ViewMsg("newsAdd", None.toMap[Any, Any], req, res, ctx)
    }

    // catch all
    case ignore =>
    {
      println(getClass() + ": ignoring the following: " + ignore)
    }
  }
}

object NewsController {

  // Count of actors that will balance the load
  val ACTORS_COUNT = 100

  /*
   * Initialization of the smart work stealing dispatcher that polls messages from
   * the mailbox of a busy actor and finds other actor in the pool that can process
   * the message.
   */
  val workStealingDispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pooled-dispatcher")
  var dispatcher = workStealingDispatcher
  .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
  .setCorePoolSize(ACTORS_COUNT)
  .buildThreadPool

  /*
   * Creates list of actors the will be supervized
   */
  def createListOfSupervizedActors(poolSize: Int): List[Supervise] = {
    (1 to poolSize toList).foldRight(List[Supervise]()) {
      (i, list) => Supervise(Actor.actorOf( { new NewsController() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = ActorRegistry.actorsFor[NewsController](classOf[NewsController]).head

  // supervisor.stop
  // supervisor.shutdown
}
