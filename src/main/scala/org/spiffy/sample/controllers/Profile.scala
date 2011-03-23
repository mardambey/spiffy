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

import org.spiffy.http._
import org.spiffy.validation._
import org.spiffy.sample.validation._

import java.util.LinkedList

/**
 * The profile controller handles requests that deal with fetching and
 * updating profile information.
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
class ProfileController 
  extends Actor 
  with Validation
  with ValidationHelpers
   {
  /**
   * Set the dispatcher
   */
  self.dispatcher = ProfileController.dispatcher

  /**
   * Handles all incoming messages:
   * <br>
   * <ul>
   *  <li>checkProfile</li>
   *  <li>updateProfile</li>
   * </ul>
   */
  def receive = {
    // checks if a profile is valid
    case List(List("checkProfile"), req: SpiffyRequestWrapper, res: SpiffyResponseWrapper, ctx:AsyncContext) => {
      var errors:Map[String, Set[String]] = None.toMap

      validate (req) (
	"email" as email confirmedBy "email_confirm",
	"email_confirm" as email,
	"title" as (string, 16) optional
      ) match {
	case Some(List(errs,warns)) => { // Problem validating
	  errors = errs
	}
	case None => { // No problems
	  errors = None.toMap
	}
      }

      // validating all error messages in linked list
      var err = errors2LinkedList(errors)
      
      res.setContentType("text/xml")
      val map = Map("response" -> "false", "errors" -> err.getOrElse(null))
      // TODO: check if the given profile information is valid      
      FreemarkerViewHandler() ! List("checkProfile", map, List(req, res, ctx))
    }

    // updates the profile identified by the profile key
    case List("updateProfile", req: SpiffyRequestWrapper, res: SpiffyResponseWrapper, ctx:AsyncContext) => {
      res.getWriter.write("in logout!")
      ctx.complete
      // TODO: this should send its data back using a view
    }   

    // catch all
    case ignore =>
    {
      println("ProfileController ignoring the following: " + ignore)
    }
  }
}

object ProfileController {

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
      (i, list) => Supervise(Actor.actorOf( { new ProfileController() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = ActorRegistry.actorsFor[ProfileController](classOf[ProfileController]).head

  // supervisor.stop
  // supervisor.shutdown
}
