package org.spiffy.http

import collection.JavaConversions._
import collection.mutable.{ConcurrentMap => CMap}
import java.util.concurrent.{ConcurrentHashMap => JCMap}
import akka.actor.ActorRef

/**
 * The ClientMap holds references to all the connection clients. It
 * maps the session id to an actor representing the client. The actor
 * can be local or remote.
 */
trait ClientMap {
  def get(key: String): Some[ActorRef]

  def put(key: String, actor: ActorRef)

  def delete(key: String)

  def contains(key:String) : Boolean

  def foreach (f: ((String, ActorRef)) => Unit)
}

/**
 * Provides a local implementation for the ClientMap that is
 * backed via a ConcurrentHashMap.
 */
class LocalClientMap extends ClientMap {

  /**
   * Maps sessions to actors (data associated with session)
   */
  val sessions: CMap[String, ActorRef] = new JCMap[String, ActorRef]()

  def get(key: String): Some[ActorRef] = {
    Some(sessions(key))
  }

  def put(key: String, actor: ActorRef) {
    sessions.put(key, actor)
  }

  def delete(key: String) {
    sessions.remove(key)
  }

  def contains(key:String): Boolean = sessions.contains(key)

  def foreach (f: ((String, ActorRef)) => Unit) {
    sessions.foreach(c => f(c))
  }
}

/**
 * Uses memcached (or any similar datastore) to map user 
 * identifers to their actor (through a value in the datastore that
 * tells us where the client is). Maintains a local map where the 
 * values are a local or remote actor. As long as an actor is 
 * "pingable" it will not consult memcached. If the actor is 
 * no longer "pingable" it will query memcached for its new 
 * location and attempt to get a remote actor for that location.
 */
class ClusteredClientMap {

}
