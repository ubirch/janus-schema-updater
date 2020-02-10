package main.scala.com.ubirch.upgrader.core.janusgraph

import gremlin.scala.{ScalaGraph, TraversalSource}
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

trait GremlinConnector {
  def graph: ScalaGraph
  def g: TraversalSource
  def b: Bindings
  def closeConnection()
}

