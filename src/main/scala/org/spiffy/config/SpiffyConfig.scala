package org.spiffy.config

import akka.actor.Actor
import scala.util.matching.Regex
import org.spiffy.http._

// controllers in use
import org.spiffy.sample.controllers._

object SpiffyConfig {

  /**
   * Full path where the web application is deployed
   * TODO: make this automatic from context
   */
  var WEBROOT = "/home/hisham/local/apache-tomcat-7.0.5/webapps/spiffy"

 /**
   * Application root
   * TODO: move this into some config
   */
  val APPROOT = "/spiffy"
  
  /**
   * Convenience variable representing the length of the application root
   */
  val APPROOT_LENGTH = APPROOT.length

  /**
   * The timeout value for the asynchronous request in microseconds
   */
  val ASYNC_TIMEOUT = 1000

  /**
   * Actor to be notified in case of a 404.
   */
  val NOT_FOUND_ACTOR = Actor.actorOf[HttpErrorHandler].start

  /**
   * Actor responsible for rendeing views
   */
  val VIEW_HANDLER_ACTOR = FreemarkerViewHandler()

  /**
   * Actor responsible for routing requests
   */
  val ROUTER_ACTOR = Router()

 /**
   * Map holding routes. A route is a regular expression that maps to a
   * Scala Any object. See documentation for the router class for details
   * on what the regex can map to.
   */
  val ROUTES = Map[Regex, Any](

    // index page, no code, just text
    """^/$""".r -> "Welcome to Spiffy!",

    // login
    new Regex("""^/(login)/$""") -> AuthController(),

    // logout
    new Regex("""^/(logout)/$""") -> AuthController(),

    // checkProfile
    new Regex("""^/(checkProfile)/$""") -> ProfileController()
  )
}
