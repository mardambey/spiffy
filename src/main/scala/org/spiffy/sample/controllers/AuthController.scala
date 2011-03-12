package org.spiffy.sample.controllers

import scala.util.matching.Regex
import javax.servlet._
import http.{HttpServletRequestWrapper, HttpServletResponse, HttpServletRequest}
import akka.actor.Actor
import Console._
import org.spiffy.http._
import akka.actor.Actor
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.ActorRegistry
import akka.actor.SupervisorFactory
import akka.config.Supervision._
import org.spiffy.actor._

/**
 * The authentication controller logs people in and out.
 * @author Hisham Mardam-Bey <hisham.mardambey@gmail.com>
 */
class AuthController extends Actor
{
  /**
   * Set the dispatcher
   */
  self.dispatcher = AuthController.dispatcher

  /**
   * Handles all incoming messages:
   * <br>
   * <ul>
   *  <li>login</li>
   *  <li>logout</li>
   * </ul>
   */
  def receive = {
    // login operations
    case List(List("login"), req: SpiffyRequestWrapper, res: SpiffyResponseWrapper, ctx:AsyncContext) => {
      // TODO: this map will eventually be sent to the view for replacements
      val map = Map("foo" -> "foobar!!")    
      ViewHandler() ! List("login", map, List(req, res, ctx))
    }

    // logout operation
    case List("logout", req: SpiffyRequestWrapper, res: SpiffyResponseWrapper, ctx:AsyncContext) => {
      println("in logout")
      res.getWriter.write("in logout!")
      ctx.complete
      // TODO: this should send its data back using a view
    }

    // catch all
    case ignore =>
    {
      println("ignore!! " + ignore)
    }
  }
}

object AuthController {

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
      (i, list) => Supervise(Actor.actorOf( { new AuthController() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = ActorRegistry.actorsFor[AuthController](classOf[AuthController]).head

  // supervisor.stop
  // supervisor.shutdown

}
