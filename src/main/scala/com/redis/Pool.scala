package com.redis

import org.apache.commons.pool._
import org.apache.commons.pool.impl._
import com.redis.cluster.ClusterNode

private [redis] class RedisClientFactory(val addr: NodeAddress, val database: Int = 0, val secret: Option[Any] = None)
  extends PoolableObjectFactory[RedisClient] {

  // when we make an object it's already connected
  def makeObject = {
    val cl = new RedisClient(addr)
    if (database != 0)
      cl.select(database)
    secret.foreach(cl auth _)
    cl
  }

  // quit & disconnect
  def destroyObject(rc: RedisClient): Unit = {
    rc.quit // need to quit for closing the connection
    rc.disconnect // need to disconnect for releasing sockets
  }

  // noop: we want to have it connected
  def passivateObject(rc: RedisClient): Unit = {}
  def validateObject(rc: RedisClient) = rc.connected == true

  // noop: it should be connected already
  def activateObject(rc: RedisClient): Unit = {}
}

class RedisClientPool(val addr: NodeAddress, val maxIdle: Int = 8, val database: Int = 0, val secret: Option[Any] = None) {
  def host: String = addr.addr._1
  def port: Int = addr.addr._2

  def this(host: String, port: Int) =
    this(new FixedAddress(host, port))

  val pool = new StackObjectPool(new RedisClientFactory(addr, database, secret), maxIdle)
  override def toString = addr.toString

  def withClient[T](body: RedisClient => T) = {
    val client = pool.borrowObject
    try {
      body(client)
    } finally {
      pool.returnObject(client)
    }
  }

  // close pool & free resources
  def close = pool.close
}

/**
 *
 * @param poolname must be unique
 */
class IdentifiableRedisClientPool(val node: ClusterNode)
  extends RedisClientPool (new FixedAddress(node.host, node.port), node.maxIdle, node.database, node.secret) {
  override def toString = node.nodename
}
