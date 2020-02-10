package main.scala.com.ubirch.upgrader.core.operations

import gremlin.scala.{Edge, Key, Vertex}
import gremlin.scala.GremlinScala.Aux
import main.scala.com.ubirch.upgrader.core.config.Elements._
import main.scala.com.ubirch.upgrader.core.janusgraph.GremlinConnector
import shapeless.HNil


object JanusOps {

  private def getUppType(uppType: String)(implicit gc: GremlinConnector): List[Vertex] = {
    gc.g.V().has(Key[String](typeProp), uppType).l()
  }

  def getDevices(implicit gc: GremlinConnector): List[Vertex] = {
    getUppType(deviceProp)
  }

  def getAllEdgesVertices(device: Vertex)(implicit gc: GremlinConnector): List[(Edge, Vertex)] = {
    val edges = getEdgesAssociatedToDevice(device)
    edges.map{e => (e, getUppAssociatedToEdge(e))}
  }

  def getEdgesAssociatedToDevice(device: Vertex)(implicit gc: GremlinConnector): List[Edge] = {
    gc.g.V(device).inE().l()
  }

  def getUppAssociatedToEdge(edge: Edge)(implicit gc: GremlinConnector): Vertex = {
    gc.g.E(edge).outV().l().head
  }

  def getUppTimestamp(vertex: Vertex)(implicit gc: GremlinConnector): Long = {
    gc.g.V(vertex).value[Long](timestampProp).head()
  }

  def putUppTimestampOnEdge(upp: Vertex, edge: Edge)(implicit gc: GremlinConnector): Aux[Edge, HNil] = {
    putTimestampOnEdge(getUppTimestamp(upp), edge)
  }

  def putTimestampOnEdge(timestamp: Long, edge: Edge)(implicit gc: GremlinConnector): Aux[Edge, HNil] = {
    gc.g.E(edge).property(Key[Long](timestampProp), timestamp).iterate()
  }




}
