package org.spiffy.config

import akka.actor.{Actor,ActorRef}
import scala.util.matching.Regex
import scala.reflect.New

import org.spiffy.http._
import org.spiffy.Helpers.companion
import org.spiffy.{WorkStealingSupervisedDispatcherService => pool}

import javax.naming.InitialContext

// controllers in use
import org.spiffy.sample.controllers._

/**
 * Used to get a handle on the configuation object
 */
object SpiffyConfig {

  /**
   * If all else fails, use this, pretty useless unless you are working on Spiffy
   */
  final val SPIFFY_BUILTIN_CFG = "org.spiffy.config.SpiffyBuiltinConfig"
  
  val configClass:String = {
    try {
      val ctx = new InitialContext()
      ctx.lookup("java:comp/env/SpiffyConfigObject").asInstanceOf[String]
    } catch {
      case e:Exception => {
	SPIFFY_BUILTIN_CFG
      }
    }
  }
  
  val result = companion[SpiffyConfig](configClass).apply
  def apply() = result
}

trait SpiffyConfig {

  /**
   * Returns ourselves
   */
  def apply():SpiffyConfig

  /**
   * Full path where the web application is deployed
   * TODO: make this automatic from context
   */
  val WEBROOT:String

 /**
   * Application root
   * TODO: move this into some config
   */
  val APPROOT:String
  
  /**
   * Convenience variable representing the length of the application root
   */
  lazy val APPROOT_LENGTH = APPROOT.length

  /**
   * The timeout value for the asynchronous request in microseconds
   */
  val ASYNC_TIMEOUT:Int

  /**
   * Actor to be notified in case of a 404.
   */
  val NOT_FOUND_ACTOR:ActorRef

  /**
   * Actor responsible for rendeing views
   */
  val VIEW_HANDLER_ACTOR:ActorRef

  /**
   * Actor responsible for routing requests
   */
  val ROUTER_ACTOR:ActorRef

 /**
   * Map holding routes. A route is a regular expression that maps to a
   * Scala Any object. See documentation for the router class for details
   * on what the regex can map to.
   */
  val ROUTES:Map[Regex, Any]
}

/**
 * Spiffy's built in configuration, needs to be tweaked by user.
 */
object SpiffyBuiltinConfig extends SpiffyConfig {
  def apply = SpiffyBuiltinConfig

  /**
   * Full path where the web application is deployed
   * TODO: make this automatic from context
   */
  val WEBROOT = "/home/hisham/local/apache-tomcat-7.0.5/webapps/spiffy"

 /**
   * Application root
   * TODO: move this into some config
   */
  val APPROOT = "/spiffy"

  /**
   * The timeout value for the asynchronous request in microseconds
   */
  val ASYNC_TIMEOUT = 5000

  /**
   * Actor to be notified in case of a 404.
   */
  lazy val NOT_FOUND_ACTOR = HttpErrorHandler()

  /**
   * Actor responsible for rendeing views
   */
  lazy val VIEW_HANDLER_ACTOR = ScalateViewHandler()

  /**
   * Actor responsible for routing requests
   */
  lazy val ROUTER_ACTOR = Router()

 /**
   * Map holding routes. A route is a regular expression that maps to a
   * Scala Any object. See documentation for the router class for details
   * on what the regex can map to.
   */
  val ROUTES = Map[Regex, Any](

    // index page, no code, just text
    """^/$""".r -> "Welcome to Spiffy!",

    // main news page
    new Regex("""^/(news)/$""") -> pool(classOf[NewsController], 100),

    // form to add some news
    new Regex("""^/(news)/(add)/$""") -> pool(classOf[NewsController], 100),

    // save news, doesnt really save, just shows confirmation
    new Regex("""^/(news)/(save)/$""") -> pool(classOf[NewsController], 100),
  
    // view some news by id
    new Regex("""^/(news)/(view)/(\d+)/$""") -> pool(classOf[NewsController], 100),

    // another way to view news by id, before hooks will catch this one
    new Regex("""^/(news)/(see)/(\d+)/$""") -> pool(classOf[NewsController], 100)
  )
}
