package main.scala.com.ubirch.upgrader.core.janusgraph

import org.apache.tinkerpop.gremlin.process.traversal.Bindings

protected class GremlinConnectorForTests extends GremlinConnector {

  implicit val graph = ??? //TinkerGraph.open().asScala
  val g = ??? // graph.traversal
  val b: Bindings = Bindings.instance

  override def closeConnection(): Unit = {} //graph.close()

}
