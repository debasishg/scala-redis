package com.redis.cluster

import java.util.zip.CRC32
import scala.collection.immutable.TreeSet
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

import com.redis._

import serialization._

/**
 * Consistent hashing distributes keys across multiple servers. But there are situations
 * like <i>sorting</i> or computing <i>set intersections</i> or operations like <tt>rpoplpush</tt>
 * in redis that require all keys to be collocated on the same server.
 * <p/>
 * One of the techniques that redis encourages for such forced key locality is called
 * <i>key tagging</i>. See <http://code.google.com/p/redis/wiki/FAQ> for reference.
 * <p/>
 * The trait <tt>KeyTag</tt> defines a method <tt>tag</tt> that takes a key and returns
 * the part of the key on which we hash to determine the server on which it will be located.
 * If it returns <tt>None</tt> then we hash on the whole key, otherwise we hash only on the
 * returned part.
 * <p/>
 * redis-rb implements a regex based trick to achieve key-tagging. Here is the technique
 * explained in redis FAQ:
 * <i>
 * A key tag is a special pattern inside a key that, if preset, is the only part of the key 
 * hashed in order to select the server for this key. For example in order to hash the key 
 * "foo" I simply perform the CRC32 checksum of the whole string, but if this key has a 
 * pattern in the form of the characters {...} I only hash this substring. So for example 
 * for the key "foo{bared}" the key hashing code will simply perform the CRC32 of "bared". 
 * This way using key tags you can ensure that related keys will be stored on the same Redis
 * instance just using the same key tag for all this keys. Redis-rb already implements key tags.
 * </i>
 */
trait KeyTag {
  def tag(key: Seq[Byte]): Option[Seq[Byte]]
}

import scala.util.matching.Regex
object RegexKeyTag extends KeyTag {

  val tagStart = '{'.toByte
  val tagEnd = '}'.toByte

  def tag(key: Seq[Byte]) = {
    val start = key.indexOf(tagStart) + 1
    if (start > 0) {
      val end = key.indexOf(tagEnd, start)
      if (end > -1) Some(key.slice(start,end)) else None
    } else None
  }
}

object NoOpKeyTag extends KeyTag {
  def tag(key: Seq[Byte]) = Some(key)
}

abstract class RedisCluster(hosts: String*) extends RedisCommand {

  // not needed at cluster level
  override val host = null
  override val port = 0

  // abstract val
  val keyTag: Option[KeyTag]

  // default in libmemcached
  val POINTS_PER_SERVER = 160 // default in libmemcached

  // instantiating a cluster will automatically connect participating nodes to the server
  val clients = hosts.toList.map {h => 
    val hp = h.split(":")
    new RedisClientPool(hp(0), hp(1).toInt)
  }

  // the hash ring will instantiate with the nodes up and added
  val hr = HashRing[RedisClientPool](clients, POINTS_PER_SERVER)

  // get node for the key
  def nodeForKey(key: Any)(implicit format: Format) = {
    val bKey = format(key)
    hr.getNode(keyTag.flatMap(_.tag(bKey)).getOrElse(bKey)).withClient { client => client }
  }

  // add a server
  def addServer(server: String) = {
    val hp = server.split(":")
    hr addNode new RedisClientPool(hp(0), hp(1).toInt)
  }

  /**
   * Operations
   */
  override def keys[A](pattern: Any = "*")(implicit format: Format, parse: Parse[A]) =
    Some(hr.cluster.toList.map(_.withClient(_.keys[A](pattern))).flatten.flatten)

  def onAllConns[T](body: RedisClient => T) = 
    hr.cluster.map(p => p.withClient { client => body(client) }) // .forall(_ == true)

  override def flushdb = onAllConns(_.flushdb) forall(_ == true)
  override def flushall = onAllConns(_.flushall) forall(_ == true)
  override def quit = onAllConns(_.quit) forall(_ == true)
  def close = hr.cluster.map(_.close)

  override def rename(oldkey: Any, newkey: Any)(implicit format: Format): Boolean = nodeForKey(oldkey).rename(oldkey, newkey)
  override def renamenx(oldkey: Any, newkey: Any)(implicit format: Format): Boolean = nodeForKey(oldkey).renamenx(oldkey, newkey)
  override def dbsize: Option[Long] =
    Some(onAllConns(_.dbsize).foldLeft(0l)((a, b) => b.map(a+).getOrElse(a)))
  override def exists(key: Any)(implicit format: Format): Boolean = nodeForKey(key).exists(key)
  override def del(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    Some((key :: keys.toList).groupBy(nodeForKey).foldLeft(0l) { case (t,(n,ks)) => n.del(ks.head,ks.tail:_*).map(t+).getOrElse(t) })
  override def getType(key: Any)(implicit format: Format) = nodeForKey(key).getType(key)
  override def expire(key: Any, expiry: Int)(implicit format: Format) = nodeForKey(key).expire(key, expiry)
  override def select(index: Int) = throw new UnsupportedOperationException("not supported on a cluster")

  /**
   * NodeOperations
   */
  override def save = onAllConns(_.save) forall(_ == true)
  override def bgsave = onAllConns(_.bgsave) forall(_ == true)
  override def shutdown = onAllConns(_.shutdown) forall(_ == true)
  override def bgrewriteaof = onAllConns(_.bgrewriteaof) forall(_ == true)

  override def lastsave = throw new UnsupportedOperationException("not supported on a cluster")
  override def monitor = throw new UnsupportedOperationException("not supported on a cluster")
  override def info = throw new UnsupportedOperationException("not supported on a cluster")
  override def slaveof(options: Any) = throw new UnsupportedOperationException("not supported on a cluster")
  override def move(key: Any, db: Int)(implicit format: Format) = throw new UnsupportedOperationException("not supported on a cluster")
  override def auth(secret: Any)(implicit format: Format) = throw new UnsupportedOperationException("not supported on a cluster")


  /**
   * StringOperations
   */
  override def set(key: Any, value: Any)(implicit format: Format) = nodeForKey(key).set(key, value)
  override def get[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).get(key)
  override def getset[A](key: Any, value: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).getset(key, value)
  override def setnx(key: Any, value: Any)(implicit format: Format) = nodeForKey(key).setnx(key, value)
  override def incr(key: Any)(implicit format: Format) = nodeForKey(key).incr(key)
  override def incrby(key: Any, increment: Int)(implicit format: Format) = nodeForKey(key).incrby(key, increment)
  override def decr(key: Any)(implicit format: Format) = nodeForKey(key).decr(key)
  override def decrby(key: Any, increment: Int)(implicit format: Format) = nodeForKey(key).decrby(key, increment)

  override def mget[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] = {
    val keylist = (key :: keys.toList)
    val kvs = for {
      (n, ks) <- keylist.groupBy(nodeForKey)
      vs <- n.mget[A](ks.head, ks.tail: _*).toList
      kv <- (ks).zip(vs)
    } yield kv
    Some(keylist.map(kvs))
  }

  override def mset(kvs: (Any, Any)*)(implicit format: Format) = kvs.toList.map{ case (k, v) => set(k, v) }.forall(_ == true)
  override def msetnx(kvs: (Any, Any)*)(implicit format: Format) = kvs.toList.map{ case (k, v) => setnx(k, v) }.forall(_ == true)

  /**
   * ListOperations
   */
  override def lpush(key: Any, value: Any, values: Any*)(implicit format: Format) = nodeForKey(key).lpush(key, value, values:_*)
  override def rpush(key: Any, value: Any, values: Any*)(implicit format: Format) = nodeForKey(key).lpush(key, value, values:_*)
  override def llen(key: Any)(implicit format: Format) = nodeForKey(key).llen(key)
  override def lrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).lrange[A](key, start, end)
  override def ltrim(key: Any, start: Int, end: Int)(implicit format: Format) = nodeForKey(key).ltrim(key, start, end)
  override def lindex[A](key: Any, index: Int)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).lindex(key, index)
  override def lset(key: Any, index: Int, value: Any)(implicit format: Format) = nodeForKey(key).lset(key, index, value)
  override def lrem(key: Any, count: Int, value: Any)(implicit format: Format) = nodeForKey(key).lrem(key, count, value)
  override def lpop[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).lpop[A](key)
  override def rpop[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).rpop[A](key)
  override def rpoplpush[A](srcKey: Any, dstKey: Any)(implicit format: Format, parse: Parse[A]) = 
    inSameNode(srcKey, dstKey) {n => n.rpoplpush[A](srcKey, dstKey)}

  private def inSameNode[T](keys: Any*)(body: RedisClient => T)(implicit format: Format): T = {
    val nodes = keys.toList.map(nodeForKey(_))
    nodes.forall(_ == nodes.head) match {
      case true => body(nodes.head)  // all nodes equal
      case _ => 
        throw new UnsupportedOperationException("can only occur if both keys map to same node")
    }
  }

  /**
   * SetOperations
   */
  override def sadd(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] = nodeForKey(key).sadd(key, value, values:_*)
  override def srem(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] = nodeForKey(key).srem(key, value, values:_*)
  override def spop[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).spop[A](key)

  override def smove(sourceKey: Any, destKey: Any, value: Any)(implicit format: Format) = 
    inSameNode(sourceKey, destKey) {n => n.smove(sourceKey, destKey, value)}

  override def scard(key: Any)(implicit format: Format) = nodeForKey(key).scard(key)
  override def sismember(key: Any, value: Any)(implicit format: Format) = nodeForKey(key).sismember(key, value)

  override def sinter[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sinter[A](key, keys: _*)}

  override def sinterstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sinterstore(key, keys: _*)}

  override def sunion[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sunion[A](key, keys: _*)}

  override def sunionstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sunionstore(key, keys: _*)}

  override def sdiff[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sdiff[A](key, keys: _*)}

  override def sdiffstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sdiffstore(key, keys: _*)}

  override def smembers[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).smembers(key)
  override def srandmember[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).srandmember(key)


  import Commands._
  import RedisClient._

  /**
   * SortedSetOperations
   */
  override def zadd(key: Any, score: Double, member: Any, scoreVals: (Double, Any)*)(implicit format: Format) = 
    nodeForKey(key).zadd(key, score, member, scoreVals:_*)
  override def zrem(key: Any, member: Any, members: Any*)(implicit format: Format): Option[Long] = 
    nodeForKey(key).zrem(key, member, members)
  override def zincrby(key: Any, incr: Double, member: Any)(implicit format: Format) = nodeForKey(key).zincrby(key, incr, member)
  override def zcard(key: Any)(implicit format: Format) = nodeForKey(key).zcard(key)
  override def zscore(key: Any, element: Any)(implicit format: Format) = nodeForKey(key).zscore(key, element)
  override def zrange[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder )(implicit format: Format, parse: Parse[A]) = 
    nodeForKey(key).zrange[A](key, start, end, sortAs)
  override def zrangeWithScore[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    nodeForKey(key).zrangeWithScore[A](key, start, end, sortAs)

  override def zrangebyscore[A](key: Any, min: Double = Double.NegativeInfinity, 
                                minInclusive: Boolean = true, max: Double = Double.PositiveInfinity, 
                                maxInclusive: Boolean = true, limit: Option[(Int, Int)], 
                                sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    nodeForKey(key).zrangebyscore[A](key, min, minInclusive, max, maxInclusive, limit, sortAs)

  override def zrangebyscoreWithScore[A](key: Any, min: Double = Double.NegativeInfinity, 
                                minInclusive: Boolean = true, max: Double = Double.PositiveInfinity, 
                                maxInclusive: Boolean = true, limit: Option[(Int, Int)], 
                                sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    nodeForKey(key).zrangebyscoreWithScore[A](key, min, minInclusive, max, maxInclusive, limit, sortAs)

  override def zcount(key: Any, min: Double = Double.NegativeInfinity, max: Double = Double.PositiveInfinity, minInclusive: Boolean = true, maxInclusive: Boolean = true)(implicit format: Format): Option[Long] =
    nodeForKey(key).zcount(key, min, max, minInclusive, maxInclusive)

  /**
   * HashOperations
   */
  override def hset(key: Any, field: Any, value: Any)(implicit format: Format) = nodeForKey(key).hset(key, field, value)
  override def hget[A](key: Any, field: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).hget[A](key, field)
  override def hmset(key: Any, map: Iterable[Product2[Any, Any]])(implicit format: Format) = nodeForKey(key).hmset(key, map)
  override def hmget[K,V](key: Any, fields: K*)(implicit format: Format, parseV: Parse[V]) = nodeForKey(key).hmget[K,V](key, fields:_*)
  override def hincrby(key: Any, field: Any, value: Int)(implicit format: Format) = nodeForKey(key).hincrby(key, field, value)
  override def hexists(key: Any, field: Any)(implicit format: Format) = nodeForKey(key).hexists(key, field)
  override def hdel(key: Any, field: Any, fields: Any*)(implicit format: Format): Option[Long] = nodeForKey(key).hdel(key, field, fields:_*)
  override def hlen(key: Any)(implicit format: Format) = nodeForKey(key).hlen(key)
  override def hkeys[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).hkeys[A](key)
  override def hvals[A](key: Any)(implicit format: Format, parse: Parse[A]) = nodeForKey(key).hvals[A](key)
  override def hgetall[K,V](key: Any)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]) = nodeForKey(key).hgetall[K,V](key)
}
