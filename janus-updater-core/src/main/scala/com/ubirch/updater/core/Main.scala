package com.ubirch.updater.core

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.updater.core.janusgraph.{ ConnectorType, GremlinConnector, GremlinConnectorFactory }
import com.ubirch.updater.core.operations.JanusOps._
import gremlin.scala.Edge

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {

    logger.info("Starting janus upgrader.")
    logger.info("Connecting to janusgraph server.")
    implicit val gc: GremlinConnector = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)

    //find all non-updates edges
    doItForAllEdges()

    // update UPP->DEVICE
    //    val devices = getDevices
    //    logger.info("Getting devices")
    //    logger.info(s"Got ${devices.size} devices")
    //    processAsynch(devices, treatDevice, 1)
    //    logger.info("------- FINISHED UPP->DEVICE -------")

    // update CHAIN = UPP->UPP
    //    logger.info("Starting CHAIN")
    //    val upps = getUpps
    //    logger.info(s"Found ${upps.size} UPPs")
    //    processAsynch(upps, treatChain)
    //    logger.info("------- FINISHED CHAIN -------")

    // update SLAVE_TREE->UPP
    /*    logger.info("Starting SLAVE_TREE->UPP")
    val slaves = getSlaves
    logger.info(s"Found ${slaves.size} SLAVE_TREEs")
    processAsynch(slaves, treatSlavesUpp)
    logger.info("------- FINISHED SLAVE_TREE->UPP -------")*/

    // update SLAVE_TREE->SLAVE_TREE
    /* logger.info("Starting SLAVE_TREE->SLAVE_TREE")
    logger.info(s"Found ${slaves.size} SLAVE_TREEs")
    processAsynch(slaves, treatSlaveSlave)
    logger.info("------- FINISHED SLAVE_TREE->SLAVE_TREE -------")*/

    /// update MASTER_TREE->SLAVE_TREE
    /*    logger.info("Starting MASTER_TREE->SLAVE_TREE")
    val masters: List[Vertex] = getMasters
    logger.info(s"Found ${masters.size} MASTER_TREEs")
    processAsynch(masters, treatMasterSlave)
    logger.info("------- FINISHED MASTER_TREE->SLAVE_TREE -------")*/

    /*// update MASTER_TREE->MASTER_TREE
    logger.info("Starting MASTER_TREE->MASTER_TREE")
    logger.info(s"Found ${masters.size} MASTER_TREEs")
    processAsynch(masters, treatMasterMaster)
    logger.info("------- FINISHED MASTER_TREE->MASTER_TREE -------")

    // update PUBLIC_CHAIN->MASTER_TREE
    logger.info("Starting PUBLIC_CHAIN->MASTER_TREE")
    val bcxs = getBcs
    logger.info(s"Found ${bcxs.size} PUBLIC_CHAINs")
    processAsynch(bcxs, treatBcMaster)
    logger.info("------- FINISHED PUBLIC_CHAIN->MASTER_TREE -------")*/

    logger.info("------- FINISHED -------")
  }

  def doItForAllEdges()(implicit gc: GremlinConnector): Unit = {
    var edges: List[Edge] = getEdgesWithouthTimestamp()
    var counter = 0
    val limit = 2000
    while (edges.nonEmpty) {
      logger.info(s"having ${edges.size} edges")
      val (le2: List[Edge], _) = parallel(getEdgesWithouthTimestamp(limit), processEdgesAsynch(edges, treatEdgesWithoutTimestamp, counter))
      counter += limit
      edges = le2
    }
  }

  def parallel[A, B](taskA: => A, taskB: => B): (A, B) = {
    val fB: Future[B] = Future { taskB }
    val a: A = taskA
    val b: B = Await.result(fB, Duration.Inf)
    (a, b)
  }

}