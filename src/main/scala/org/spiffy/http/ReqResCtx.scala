package org.spiffy.http

import javax.servlet.AsyncContext

case class ReqResCtx(req:SpiffyRequestWrapper, res:SpiffyResponseWrapper, ctx:AsyncContext)
