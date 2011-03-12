package org.spiffy.actor

import collection.mutable.LinkedList
import akka.actor.{MaximumNumberOfRestartsWithinTimeRangeReached, ActorRef, Actor}

/**
 * @author Garrick Evans
 *
 * These traits are used to build up what I call a scaling load balancing actor which
 *  is one that acts as a proxy for routing messages to a pool of delegates.
 * Two main characteristics are considered: capacity & scaling
 * A selector is mixed in to elect a delegate candidate - provided is one that performs
 *  smallest mailbox based on the existing Akka algorithm, others are, as it were,
 *  left as an exercise.
 *
 * Example:
 *
 *

    //
    // first define the balancer itself,
    // this one does smallest mailbox selection and is bounded (limited delegate actors)
    // the pressure function that drives the scaler is keying off of the number of active futures
    // and scaling will be according to some basic sawtooth distribution
    //

  class BalancedActor extends Actor
                      with DefaultScalingLoadBalancer
                        with SmallestMailboxSelectorStrategy
                        with PressureBoundedCapacityStrategy
                          with BoundedCapacity
                          with ActiveFuturesPressure
                            with BasicLinearScaler
  {
      //
      // ScalingLoadBalancer API
      //

    val factory = new DelegateFactory

      //
      // BoundedCapacity API
      //
    def min = Runtime.getRuntime.availableProcessors
    def max = Runtime.getRuntime.availableProcessors * 16

    def receive = _recvSLB
  }

    //
    // next, i like to define the companion to make it easy to get at the proxy
    //  but that's just me...
    //

  object BalancedActor
  {
    private val _actor =
      {
        val a = Actor.actorOf(new BalancedActor).start
        _supervisor.link(a)
        a
      }

    def apply() = _actor
  }


    //
    // used by the balancer above to generate new delegate actors when necessary
    //

  class DelegateFactory extends ActorFactory
  {
    def build = Actor.actorOf(new Delegate)
  }


    //
    // finally, the actual worker...
    //

  class Delegate extends Actor
  {
    def recv = { case Foo => ... }
  }


  Usage:

    BalancedActor() ! Foo

 */


trait ActorFactory
{
  def build:ActorRef
}

trait ScalingLoadBalancer
{
  def factory:ActorFactory
  def capacity(pool:Seq[ActorRef]):Int
  def select(pool:Seq[ActorRef]):(Iterator[ActorRef], Int)

}

object ScalingLoadBalancer
{
  case object StatusRequest
  case class  Status(poolSize:Int, lastCapacityChange:Int, lastSelectorCount:Int)
}


trait DefaultScalingLoadBalancer extends ScalingLoadBalancer
{
  this: Actor =>

  import ScalingLoadBalancer._

  protected var _pool = LinkedList[ActorRef]()

  private var _lastCapacityChange = 0
  private var _lastSelectorCount = 0

  override def postStop =
    {
      _pool foreach {_ stop}
    }

  protected def _recvSLB:Receive =
    {
      case StatusRequest => self reply_? Status(_pool size, _lastCapacityChange, _lastSelectorCount)

      case max:MaximumNumberOfRestartsWithinTimeRangeReached =>
        {
          log.error("Pooled actor will be removed after maxium number of restart retries.  ACTOR("+max.victim.toString)
          _pool = _pool filter {actor => (actor.uuid != max.victim.uuid)}
        }

      case msg =>
        {
          _capacity
          _select foreach {actor =>
            self.senderFuture match
            {
              case None => actor ! msg
              case Some(future) =>
              {
                Actor.spawn {
                  try
                  {
                    (actor !! msg) match
                    {
                      case Some(result) => future completeWithResult result
                      case None => future completeWithResult None
                    }
                  }
                  catch
                  {
                    case ex => future completeWithException(ex)
                  }
                }
              }
            }
          }
        }
    }

  private def _capacity =
    {
      _lastCapacityChange = capacity(_pool)
      _lastCapacityChange match
      {
        case n if (n>0) =>
          {
            _pool ++= {
              for (a <- 0 until n) yield {
                val next = factory.build
                self startLink next
                next
              }
            }

            log.info("Pool capacity increased by "+_lastCapacityChange)
          }
        case n if (n<0) =>
          {
            val s = _pool splitAt(n)
            _pool = s._2
            s._1 foreach {_ stop}

            log.info("Pool capacity decreased by "+(-1*_lastCapacityChange))
          }
        case _ => {}

      }
    }

  private def _select =
    {
      val selection = select(_pool)
      _lastSelectorCount = selection._2
      selection._1
    }
}


// capacitors

trait ActorCapacityStrategy
{
  def capacity(pool:Seq[ActorRef]):Int
}

trait BoundedCapacity
{
  def min:Int
  def max:Int

  /**
   * returns the number of new actors to provision, clamped by the pool limits
   */
  def bounds(pool:Seq[ActorRef])(requested:(Seq[ActorRef]) => Int):Int =
    {
      val size = pool size
      val req = requested(pool)

      if (size < min) math.max(req, min-size)
      else if (size > max) math.min(req, size-max)
      else 0
    }
}

trait MailboxPressure
{
  /**
   * mailbox backlog to filter against
   */
  def threshold:Int

  /**
   * turns the number of actors with backlog into a requested number of new actors to provision
   */
  def scaler(count:Int, avgTimeout:Long, size:Int):Int

  /**
   * evaluates the number of actors to provision based on pressure caused by unread messages
   */
  def pressure(pool:Seq[ActorRef]):Int =
    {
      scaler(pool.filter(a => a.mailboxSize > threshold).size, 0, pool.size)
    }
}

trait ActiveFuturesPressure
{
  /**
   * turns the number of actors with active futures into a requested number of new actors to provision
   */
  def scaler(count:Int, avgTimeout:Long, size:Int):Int

  /**
   * evaluates the number of actors to provision based on pressure caused by blocking messages
   */
  def pressure(pool:Seq[ActorRef]):Int =
    {
      var n = 0
      var to = 0L
      pool foreach {_.senderFuture foreach {
        future => if (!future.isCompleted)
          {
            n += 1
            to += future.timeoutInNanos
          }
        }
      }
      scaler(n, if (n>0) to/n else 0, pool.size)
    }
}

trait BoundedCapacityStrategy
{
  def bounds(pool:Seq[ActorRef])(requested:(Seq[ActorRef]) => Int):Int

  def capacity(pool:Seq[ActorRef]):Int = bounds(pool)((x:Seq[ActorRef]) => 0)
}

trait PressureBoundedCapacityStrategy
{
  def bounds(pool:Seq[ActorRef])(requested:(Seq[ActorRef]) => Int):Int
  def pressure(pool:Seq[ActorRef]):Int

  def capacity(pool:Seq[ActorRef]):Int =
    {
      bounds(pool)(pressure)
    }
}


// scalers


trait BasicLinearScaler
{
  /**
   * turns the number of actors with backlog into a requested number of new actors to provision
   */
  def scaler(count:Int, avgTimeout:Long, size:Int):Int =
    {
        //
        // simple algorithm is to balance at 50% capacity
        //  and reduce or expand delegates based on under/over commit
        //  e.g.
        //    at 25% of capacity, the current pool would shrink by 25% [of the current delegate count]
        //    at 90% of capacity, the current pool would grow by 40% [of the current delegate count]
        //
      if (size > 0) math.floor(count * ((count/size) - 0.50)) toInt else 0
    }
}


// selectors


trait ActorSelectorStrategy
{
  def select(pool:Seq[ActorRef]):(Iterator[ActorRef],Int)
}

trait SmallestMailboxSelectorStrategy
{
  def count:Int = 1

  def select(pool:Seq[ActorRef]):(Iterator[ActorRef],Int) =
    {
      val set = pool.sortWith((a,b) => a.mailboxSize < b.mailboxSize).take(count)
      (set.iterator, set.size)
    }
}
