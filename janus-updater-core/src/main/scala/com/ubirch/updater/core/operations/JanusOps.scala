package com.ubirch.updater.core.operations

import java.util.Date
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.typesafe.scalalogging.LazyLogging
import gremlin.scala.{Edge, Key, Vertex}
import com.ubirch.updater.core.config.Elements._
import com.ubirch.updater.core.janusgraph.GremlinConnector

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object JanusOps extends LazyLogging {

  var uppCount = 0

  private def getVertexType(uppType: String)(implicit gc: GremlinConnector): List[Vertex] = {
    logger.info(s"Getting vertices of type $uppType")
    val res = gc.g.V().has(Key[String](typeProp), uppType).l()
    logger.info(s"Found ${res.size} vertices of type $uppType")
    res
  }

  def getDevices(implicit gc: GremlinConnector): List[Vertex] = {
    getVertexType(deviceProp)
  }

  def getUpps(implicit gc: GremlinConnector): List[Vertex] = {
    getVertexType(uppProp)
  }

  def getSlaves(implicit gc: GremlinConnector): List[Vertex] = {
    getVertexType(stProp)
  }

  def getMasters(implicit gc: GremlinConnector): List[Vertex] = {
    getVertexType(mtProp)
  }

  def getBcs(implicit gc: GremlinConnector): List[Vertex] = {
    getVertexType(bcProp)
  }

  def getAllEdgesVerticesIn(vertex: Vertex)(implicit gc: GremlinConnector): List[(Edge, Vertex)] = {
    val edges = getEdgesAssociatedToDevice(vertex)
    logger.info(s"device has ${edges.size} edges")
    edges.map { e => (e, e.outVertex()) }
  }

  def getEdgesAssociatedToDevice(device: Vertex)(implicit gc: GremlinConnector): List[Edge] = {
    gc.g.V(device).inE().l()
  }

  def getVertexTimestamp(vertex: Vertex)(implicit gc: GremlinConnector): Try[Date] = {

    Try(gc.g.V(vertex).value[Date](timestampProp).head())

  }

  def putUppTimestampOnEdge(upp: Vertex, edge: Edge)(implicit gc: GremlinConnector): Unit = {
    uppCount += 1
    val timestamp = getVertexTimestamp(upp)
    putTimestampOnEdge(timestamp, edge)
  }

  def putTimestampOnEdge(timestamp: Try[Date], edge: Edge)(implicit gc: GremlinConnector): Unit = {
    //if (!edgeHasTimestamp(edge)) {
    if (timestamp.isFailure) {
      logger.error(s"Timestamp not found for edge ${edge.id()}")
    } else {
      try {
        gc.g.E(edge).property(Key[Date](timestampProp), timestamp.get).iterate()
      } catch {
        case e: Exception => logger.error(s"Something happened: ${e.toString}")
      }
    }
    //logger.info(s"     did edge ${edge.id()}")
    //} else {
    //  logger.info(s"     Edge ${edge.id()} already has a timestamp")
    //}
  }

  /**
    * Get the IN edge of the edge that has the specified label
    */
  def getEdgeIn(vertex: Vertex, edgeLabel: String)(implicit gc: GremlinConnector): Edge = {
    gc.g.V(vertex).inE(edgeLabel).l().head
  }

  def getEdgeOut(vertex: Vertex, edgeLabel: String)(implicit gc: GremlinConnector): Edge = {
    gc.g.V(vertex).outE(edgeLabel).l().head
  }

  def edgeHasTimestamp(edge: Edge)(implicit gc: GremlinConnector): Boolean = {
    gc.g.E(edge).has(Key[Long](timestampProp)).exists()
  }

  def processEdgesAsynchNew(edges: List[Edge], execute: Edge => Unit)(implicit gc: GremlinConnector): Unit = {
    var count = 0
    val edgesPartition: List[List[Edge]] = edges.grouped(10).toList

    edgesPartition foreach { edges =>
      //logger.info(s"STARTED processing a batch of ${edges.size} edges asynchronously")

      import scala.concurrent.ExecutionContext.Implicits.global
      val t: List[Future[Unit]] = edges.map { edge =>
        //logger.debug(s"edge: ${edge.id()}")
        val process = Future(execute(edge))
        process
      }

      val futureProcesses: Future[List[Unit]] = Future.sequence(t)

      val latch = new CountDownLatch(1)
      futureProcesses.onComplete {
        case Success(_) =>
          latch.countDown()
        case Failure(e) =>
          logger.error("Something happened", e)
          latch.countDown()
      }
      latch.await(10, TimeUnit.SECONDS)
      //logger.debug(s"FINISHED processing a batch of ${edges.size} vertices asynchronously")
      count += edges.size
      logger.info(s"Processed $count edges")
    }
  }

  def processEdgesAsynch(edges: List[Edge], execute: Edge => Unit, counter: Int = -1)(implicit gc: GremlinConnector): Unit = {
    val partitionSize = 5
    val edgesPartition = edges.grouped(partitionSize).toList
    var count = 0
    val t0 = System.currentTimeMillis()

    edgesPartition foreach { edges =>
      //logger.info(s"STARTED processing a batch of ${edges.size} edges asynchronously")
      val processesOfFutures = scala.collection.mutable.ListBuffer.empty[Future[Unit]]
      import scala.concurrent.ExecutionContext.Implicits.global
      edges.foreach { edge =>
        //logger.debug(s"edge: ${edge.id()}")
        val process = Future(execute(edge))
        processesOfFutures += process
      }

      val futureProcesses = Future.sequence(processesOfFutures)

      val latch = new CountDownLatch(1)
      futureProcesses.onComplete {
        case Success(_) =>
          latch.countDown()
        case Failure(e) =>
          logger.error("Something happened", e)
          latch.countDown()
      }
      latch.await()
      Thread.sleep(5)
      //logger.debug(s"FINISHED processing a batch of ${edges.size} vertices asynchronously")
      count += edges.size
      if (count % 50 == 0) logger.info(s" ---- 1 ---- Made $count edges in this batch of ${edges.size}")
      //logger.info(s"Processed ${counter + count} edges")
    }
    logger.info(s"Took ${System.currentTimeMillis() - t0}ms to process ${edges.size} edges")
  }

  def treatEdgesWithoutTimestamp(edge: Edge)(implicit gc: GremlinConnector): Unit = {
    // get the timestamp from the out vertex
    val outV = edge.outVertex()
    val timestamp = getVertexTimestamp(outV)
    putTimestampOnEdge(timestamp, edge)
  }

  def processAsynch(vertices: List[Vertex], execute: Vertex => Unit, groupSize: Int = 10)(implicit gc: GremlinConnector): Unit = {
    val verticesPartition = vertices.grouped(groupSize).toList

    verticesPartition foreach { vertices =>
      logger.info(s"STARTED processing a batch of ${vertices.size} vertices asynchronously for ${execute.toString()} ${execute.getClass.getName} ${execute.getClass.getCanonicalName}")
      val processesOfFutures = scala.collection.mutable.ListBuffer.empty[Future[Unit]]
      import scala.concurrent.ExecutionContext.Implicits.global
      vertices.foreach { v =>
        logger.debug(s"vertex: ${v.id()}")
        val process = Future(execute(v))
        processesOfFutures += process
      }

      val futureProcesses = Future.sequence(processesOfFutures)

      val latch = new CountDownLatch(1)
      futureProcesses.onComplete {
        case Success(_) =>
          latch.countDown()
        case Failure(e) =>
          logger.error("Something happened", e)
          latch.countDown()
      }
      latch.await()
      logger.debug(s"FINISHED processing a batch of ${vertices.size} vertices asynchronously")

    }
  }

  def treatDevice(d: Vertex)(implicit gc: GremlinConnector): Unit = {
    logger.info(s"Operating on device with id ${gc.g.V(d).head().id()}")
    val allEdgesVertices: List[(Edge, Vertex)] = getAllEdgesVerticesIn(d) //
    logger.info(s"Device ${gc.g.V(d).head().id()} has ${allEdgesVertices.size} upps")
    allEdgesVertices.foreach { ev => putUppTimestampOnEdge(ev._2, ev._1) }
    logger.info(s"Upgrade done for the ${allEdgesVertices.size} edges of device ${gc.g.V(d).head().id()}")
  }

  def treatChain(upp: Vertex)(implicit gc: GremlinConnector): Unit = {
    logger.info(s"iterating on UPP ${upp.id()}")
    try {
      val outChain = getEdgeOut(upp, edgeCHAIN)
      val timestamp = getVertexTimestamp(upp)
      putTimestampOnEdge(timestamp, outChain)
    } catch {
      case _: NoSuchElementException => logger.info(s"NoSuchElementException on UPP ${upp.id()}")
    }
  }

  def treatSlavesUpp(slave: Vertex)(implicit gc: GremlinConnector): Unit = {
    val outEdgeStUpp = getEdgeOut(slave, edgeST_UPP)
    val timestamp = getVertexTimestamp(slave)
    putTimestampOnEdge(timestamp, outEdgeStUpp)
  }

  def treatSlaveSlave(slave: Vertex)(implicit gc: GremlinConnector): Unit = {
    val outChainStSt = getEdgeOut(slave, edgeST_ST)
    val timestamp = getVertexTimestamp(slave)
    putTimestampOnEdge(timestamp, outChainStSt)
  }

  def treatMasterSlave(master: Vertex)(implicit gc: GremlinConnector): Unit = {
    val outEdgeMtSt = getEdgeOut(master, edgeMT_ST)
    val timestamp = getVertexTimestamp(master)
    putTimestampOnEdge(timestamp, outEdgeMtSt)
  }

  def treatMasterMaster(master: Vertex)(implicit gc: GremlinConnector): Unit = {
    val outEdgeMtMt = getEdgeOut(master, edgeMT_MT)
    val timestamp = getVertexTimestamp(master)
    putTimestampOnEdge(timestamp, outEdgeMtMt)
  }

  def treatBcMaster(bc: Vertex)(implicit gc: GremlinConnector): Unit = {
    val outEdgeBcMt = getEdgeOut(bc, edgeBC_MT)
    val timestamp = getVertexTimestamp(bc)
    putTimestampOnEdge(timestamp, outEdgeBcMt)
  }

  def getEdgesWithouthTimestamp(limit: Int = 1000)(implicit gc: GremlinConnector): List[Edge] = {
    logger.info(s"Finding $limit edges")
    val t0 = System.currentTimeMillis()
    val res = gc.g.E().hasNot(Key[Date](timestampProp)).limit(limit).l()
    logger.info(s"Found ${res.size} edges that do not have the timestamp property in ${System.currentTimeMillis() - t0}ms")
    res
  }

}
