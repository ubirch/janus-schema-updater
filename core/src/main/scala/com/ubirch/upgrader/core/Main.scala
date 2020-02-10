package main.scala.com.ubirch.upgrader.core

import com.typesafe.scalalogging.LazyLogging
import main.scala.com.ubirch.upgrader.core.janusgraph.{ConnectorType, GremlinConnector, GremlinConnectorFactory}
import main.scala.com.ubirch.upgrader.core.operations.JanusOps._

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {

    logger.info("Starting janus upgrader.")
    logger.info("Connecting to janusgraph server.")
    implicit val gc: GremlinConnector = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)
    logger.info("Connection to janusgraph server successful.")
    logger.info("Getting devices")
    val devices = getDevices
    logger.info(s"Got ${devices.size} devices")
    devices foreach  { d => {
      val allEdgesVertices = getAllEdgesVertices(d)//
      logger.info(s"Device ${gc.g.V(d).head().id()} has ${allEdgesVertices.size} upps")
      allEdgesVertices.foreach{ev => putUppTimestampOnEdge(ev._2, ev._1)}
      logger.info(s"Upgrade done for upps of device ${gc.g.V(d).head().id()}")
    }}

  }






}
