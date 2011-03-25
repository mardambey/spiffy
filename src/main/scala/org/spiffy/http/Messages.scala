package org.spiffy.http

import javax.servlet.AsyncContext

case class ControllerMsg(route:List[Any], req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)
case class ViewMsg(template:String, params:Map[Any, Any], req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)
