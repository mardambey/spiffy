package org.spiffy.validation

import java.util.LinkedList

trait ValidationHelpers {
  def errors2LinkedList(errors:Map[String, Set[String]]) : Option[LinkedList[String]] = {
    if (errors == None.toMap) return None
    var err = new LinkedList[String]()
    errors foreach { e => { e._2 foreach { err add _ }}}
    Some(err)
  }
}
