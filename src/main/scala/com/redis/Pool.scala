package com.redis

import org.apache.commons.pool._
import org.apache.commons.pool.impl._
import com.redis.cluster.ClusterNode

private [redis] class RedisClientFactory(val host: String, val port: Option[Int], val database: Int = 0, val secret: Option[Any] = None, val timeout : Int = 0)
  extends PoolableObjectFactory[RedisClient] {

  // when we make an object it's already connected
  def makeObject = {
    new RedisClient(host, port, database, secret, timeout)
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

class RedisClientPool(val host: String, val port: Option[Int], val maxIdle: Int = 8, val database: Int = 0, val secret: Option[Any] = None, val timeout : Int = 0) {
  val pool = new StackObjectPool(new RedisClientFactory(host, port, database, secret, timeout), maxIdle)
  override def toString = {
    port match {
      case Some(portValue) => host + ":" + String.valueOf(portValue)
      case None => "unix://" ++ host
    }
  }

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
  extends RedisClientPool (node.host, node.port, node.maxIdle, node.database, node.secret,node.timeout){
  override def toString = node.nodename
}
