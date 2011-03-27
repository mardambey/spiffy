What is Spiffy?
================

Spiffy...

* is written in Scala
* uses the fantastic Akka library and actors to scale
* uses servlet API 3.0 for asynchronous request handling
* is modular (replacing components is straight forward)
* uses DSLs to cut down on code where you don't want it
* supports request hooks around controllers

Spiffy is a web framework using Scala, Akka (a Scala actor implementation), and the Java Servelet 3.0 API. It makes use of the the async interface and aims to provide a massively parallel and scalable environment for web applications. Spiffy's various components are all based on the idea that they need to be independent minimalistic modules that do small amounts of work very quickly and hand off the request to the next component in the pipeline. After the last component is done processing the request it signals the servlet container by "completing" the request and sending it back to the client.

Quick example
-------------

Add some routes:

    // index page, no code, just text
    """^/$""".r -> "Welcome to Spiffy!",

    // main news page
    new Regex("""^/(news)/$""") -> NewsController(),

    // view some news by id
    new Regex("""^/(news)/(view)/(\d+)/$""") -> NewsController()

Write the controller:

    def receive = {
        
        // handles "news/add/"
        case ControllerMsg(List("news", "save"), req, res, ctx) => {

            // run validation on the request using Spiffy's validation DSL

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

                case None => None.toMap // passed validation        
            }
        }

        // handles "/news/"
        case ControllerMsg(List("news"), req, res, ctx) => {

            // load up news and stuff in map then render
            val params = loadNews
            view() ! ViewMsg("news", params, req, res, ctx)
        }

        // handles "/news/view/$newsId/"
        case ControllerMsg(List("news", "view", newsId), req, res, ctx) => {

            // usually you want to load the item, in this example we dont
            // load item and set the params that the view will render
            val news = loadNewsItem(newsId)
            val params:Map[Any,Any] = Map("news" -> news)

            // ask the view to render
            view() ! ViewMsg("newsView", params, req, res, ctx)
        }    
    }

Then create some templates. You can find more about this example by looking at [NewsController](https://github.com/mardambey/spiffy/blob/master/src/main/scala/org/spiffy/sample/controllers/NewsController.scala) and [SpiffyConfig](https://github.com/mardambey/spiffy/blob/master/src/main/scala/org/spiffy/config/SpiffyConfig.scala)    

At the moment, in order to use Spiffy you must build the jar (mvn jar:jar) and add it to your applications classpath. You must also define the following environment variable (context.xml will do):

    <Environment name="SpiffyConfigObject" value="org.spiffy.config.SpiffyBuiltinConfig" type="java.lang.String"/>

You need to replace `org.spiffy.config.SpiffyBuiltinConfig` with an object that extends `org.spiffy.config.SpiffyConfig` and provides all the needed values. Take a look at [SpiffyConfig](https://github.com/mardambey/spiffy/blob/master/src/main/scala/org/spiffy/config/SpiffyConfig.scala) for an example.

How does Spiffy use Scala (Akka) actors?
----------------------------------------

Spiffy relies on Akka actors to compartmentalize and isolate all of its various components. Every component in the Spiffy pipeline (except the initial filter) is comprised of a pool of Akka actors. Spiffy actors are backed by load balancing pools of different types of dispatchers.

How does a request travel through the Spiffy framework?
-------------------------------------------------------

Spiffy is implemented as a filter After the filter receives the request it puts it in asynchronous mode and sends it off to the router. The router then decides what to do with the request by inspecting the request URL and evaluating it against its list of known controllers mappings. A mapping is a regular expression that matches the requested URL and assigns it to corresponding controller. If a successful match is found, the router will message the controller with the request (and all needed objects that need to be sent along with it). At that point the router's job is done and it is free to process new incoming requests. After the controller receives the request it will perform any logic it needs on the request and can decide to end the request or pass it on to another component of Spiffy (usually a view handler). 

What are Spiffy hooks and where can they be placed?
---------------------------------------------------

Spiffy uses the concept of hooks to perform logic that can be encapsulated and ran before and after certain components of the framework's pipeline. Hooks can be ran before and after controllers and views. A hook that will run before a controller can decide to bypass the controller altogether or might simply perform some logic like authentication or modifying the request itself before the controller has the chance to work with it. A hook that runs after a controller can also re-route the
request to a different controller, modify it, or terminate it there and then by sending it back to the client. The same logic is used in front and after views where the hook can stop the view from rendering altogether or might pass the rendered output to another part of the framework that can do additional work on the rendered output.

Notes
-----

* When sending a message to the ViewHandler (for Freemarker) using a List() causes Freemarker to complain. Array can be used instead.

