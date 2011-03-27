package org.spiffy

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor._
import akka.actor.ActorRegistry
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.SupervisorFactory
import akka.config.Supervision._

import javax.servlet.AsyncContext

import org.spiffy.http._
import org.spiffy.config.SpiffyConfig

/**
 * Misc helpers and utilities.
 */
object Helpers {
  /**
   * Gives the companion object of the given string as an instane of the given manifest.
   */
  def companion[T](name : String)(implicit man: Manifest[T]) : T = 
    Class.forName(name + "$").getField("MODULE$").get(man.erasure).asInstanceOf[T]

  /**
   * Helper method that sends back an error message indicating that Spiffy has encountered a
   * page not found error (http response code 404)
   */
  def notFound(req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext) : Boolean = {
    SpiffyConfig().NOT_FOUND_ACTOR ! ((404, ReqResCtx(req, res, ctx)))
    false
  }
}

/**
 * Accepts a class that extends an Actor and a count. Instantiates,
 * supervises, and adds a work stealing dispatcher to as many
 * actors of the provided type as the count specified. The result is cached
 * and subsequent requests to the service return the cached result even if 
 * the count is changed.
 */
object WorkStealingSupervisedDispatcherService {

  /**
   * Keeps track of all the dispatchers.
   */
  val dispatchers = scala.collection.mutable.Map[Class[_], MessageDispatcher]()

  /**
   * Accepts a class type that extends an Actor and a count and creates,
   * supervises, and sets the dispatcher of the created actors. Returns
   * one of those actors.
   */
  def apply[T <: Actor](c:Class[T], actorCount:Int) : ActorRef = {
    dispatchers.getOrElseUpdate(c, createDispatcher(c, actorCount))
    ActorRegistry.actorsFor[T](c).head
  }
 
  /**
   * Creates the dispatcher and supervises all the created actors for that dispatcher.
   */
  protected def createDispatcher[T <: Actor](c:Class[T], count:Int) : MessageDispatcher = {
    val workStealingDispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pooled-dispatcher")
    val dispatch = workStealingDispatcher
    .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
    .setCorePoolSize(count)
    .build

    val supervisor = SupervisorFactory(
      SupervisorConfig(
	OneForOneStrategy(List(classOf[Exception]), 3, 1000),
	createListOfSupervizedActors(c, dispatch, count))).newInstance

    // Starts supervisor and all supervised actors
    supervisor.start

    // return dispatcher
    dispatch										 
  }

  /**
   * Creates list of actors the will be supervized
   */
  protected def createListOfSupervizedActors[T <: Actor](c:Class[T], dispatcher: MessageDispatcher, poolSize: Int): List[Supervise] = {
    def actor() : ActorRef = {
      val a = actorOf(c)
      a.dispatcher = dispatcher
      a.start
    }

    (1 to poolSize toList).foldRight(List[Supervise]()) {      
      (i, list) => Supervise(actor(), Permanent) :: list
    }
  }  
}

