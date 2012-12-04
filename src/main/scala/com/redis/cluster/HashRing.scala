package com.redis.cluster

import java.util.zip.CRC32
import scala.collection.immutable.TreeMap
import scala.collection.mutable.ArrayBuffer

/**
 * Consistent Hashing node ring abstraction.
 */
case class HashRing[T](nodes: List[T], replicas: Int) {

  val cluster = new ArrayBuffer[T]
  private var ring = TreeMap[Long, T]()

  nodes.foreach(addNode(_))

  /*
   * Adds a node to the hash ring (including a number of replicas)
   */
  def addNode(node: T) = {
    cluster += node
    (1 to replicas).foreach {replica =>
      ring += (nodeHashFor(node, replica) -> node)
    }
  }

  def replaceNode(node: T): Option[T] = {
    var replacedNode: Option[T] = None
    for (i <- 0 until cluster.size){
      if (cluster(i).toString().equals(node.toString)){
        replacedNode = Some(cluster(i))
        cluster(i) = node
      }
    }
    (1 to replicas).foreach {replica =>
      ring += (nodeHashFor(node, replica) -> node)
    }
    replacedNode
  }

  /*
   * Removes node from the ring
   */
  def removeNode(node: T) {
    cluster -= node
    (1 to replicas).foreach {replica =>
      ring -= nodeHashFor(node, replica)
    }
  }

  /**
   * Get the node responsible for the data key.
   * Can only be used if nodes exists in the ring, 
   * otherwise throws `IllegalStateException`
   */
  def getNode(key: Seq[Byte]): T = {
    if (isEmpty) throw new IllegalStateException("Can't get node for [%s] from an empty ring" format key)
    val crc = calculateChecksum(key)
    def nextClockwise: T = {
      val (ringKey, node) = ring.rangeImpl(Some(crc), None).headOption.getOrElse(ring.head)
      node
    }
    ring.getOrElse(crc, nextClockwise)
  }

  // Computes the CRC-32 of the given String
  def calculateChecksum(value: Seq[Byte]): Long = {
    val checksum = new CRC32
    checksum.update(value.toArray)
    checksum.getValue
  }

  /**
   * Is the ring empty, i.e. no nodes added or all removed.
   */
  def isEmpty: Boolean = ring.isEmpty

  private def nodeHashFor(node: T, replica: Int): Long = {
    calculateChecksum((node + ":" + replica).getBytes("UTF-8"))
  }

}

