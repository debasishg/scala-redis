package com.redis

import serialization._

trait SortedSetOperations { self: Redis =>
  
  // ZADD (Variadic: >= 2.4)
  // Add the specified members having the specified score to the sorted set stored at key.
  def zadd(key: Any, score: Double, member: Any, scoreVals: (Double, Any)*)(implicit format: Format): Option[Int] =
    send("ZADD", List(key, score, member) ::: scoreVals.toList.map(x => List(x._1, x._2)).flatten)(asInt)
  
  // ZREM (Variadic: >= 2.4)
  // Remove the specified members from the sorted set value stored at key.
  def zrem(key: Any, member: Any, members: Any*)(implicit format: Format): Option[Int] =
    send("ZREM", List(key, member) ::: members.toList)(asInt)
  
  // ZINCRBY
  // 
  def zincrby(key: Any, incr: Double, member: Any)(implicit format: Format): Option[Double] =
    send("ZINCRBY", List(key, incr, member))(asBulk(Parse.Implicits.parseDouble))
  
  // ZCARD
  // 
  def zcard(key: Any)(implicit format: Format): Option[Int] =
    send("ZCARD", List(key))(asInt)
  
  // ZSCORE
  // 
  def zscore(key: Any, element: Any)(implicit format: Format): Option[Double] =
    send("ZSCORE", List(key, element))(asBulk(Parse.Implicits.parseDouble))
  
  // ZRANGE
  // 
  import Commands._
  import RedisClient._

  def zrange[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]): Option[List[A]] =
    send(if (sortAs == ASC) "ZRANGE" else "ZREVRANGE", List(key, start, end))(asList.map(_.flatten))

  def zrangeWithScore[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]): Option[List[(A, Double)]] =
    send(if (sortAs == ASC) "ZRANGE" else "ZREVRANGE", List(key, start, end, "WITHSCORES"))(asListPairs(parse, Parse.Implicits.parseDouble).map(_.flatten))

  // ZRANGEBYSCORE
  // 
  def zrangebyscore[A](key: Any,
                       min: Double = Double.NegativeInfinity,
                       minInclusive: Boolean = true,
                       max: Double = Double.PositiveInfinity,
                       maxInclusive: Boolean = true,
                       limit: Option[(Int, Int)])(implicit format: Format, parse: Parse[A]): Option[List[A]] = {

      val limitEntries = if(!limit.isEmpty) { 
        "LIMIT" :: limit.toList.flatMap(l => List(l._1, l._2))
      } else { 
        List()
      }
      send("ZRANGEBYSCORE", key :: 
        Format.formatDouble(min, minInclusive) :: 
        Format.formatDouble(max, maxInclusive) ::
        limitEntries
      )(asList.map(_.flatten))
   }

  // ZRANK
  // ZREVRANK
  //
  def zrank(key: Any, member: Any, reverse: Boolean = false)(implicit format: Format): Option[Int] =
    send(if (reverse) "ZREVRANK" else "ZRANK", List(key, member))(asInt)

  // ZREMRANGEBYRANK
  //
  def zremrangebyrank(key: Any, start: Int = 0, end: Int = -1)(implicit format: Format): Option[Int] =
    send("ZREMRANGEBYRANK", List(key, start, end))(asInt)

  // ZREMRANGEBYSCORE
  //
  def zremrangebyscore(key: Any, start: Double = Double.NegativeInfinity, end: Double = Double.PositiveInfinity)(implicit format: Format): Option[Int] =
    send("ZREMRANGEBYSCORE", List(key, start, end))(asInt)

  // ZUNION
  //
  def zunionstore(dstKey: Any, keys: Iterable[Any], aggregate: Aggregate = SUM)(implicit format: Format): Option[Int] =
    send("ZUNIONSTORE", (Iterator(dstKey, keys.size) ++ keys.iterator ++ Iterator("AGGREGATE", aggregate)).toList)(asInt)

  def zunionstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], aggregate: Aggregate = SUM)(implicit format: Format): Option[Int] =
    send("ZUNIONSTORE", (Iterator(dstKey, kws.size) ++ kws.iterator.map(_._1) ++ Iterator.single("WEIGHTS") ++ kws.iterator.map(_._2) ++ Iterator("AGGREGATE", aggregate)).toList)(asInt)

  // ZINTERSTORE
  //
  def zinterstore(dstKey: Any, keys: Iterable[Any], aggregate: Aggregate = SUM)(implicit format: Format): Option[Int] =
    send("ZINTERSTORE", (Iterator(dstKey, keys.size) ++ keys.iterator ++ Iterator("AGGREGATE", aggregate)).toList)(asInt)

  def zinterstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], aggregate: Aggregate = SUM)(implicit format: Format): Option[Int] =
    send("ZINTERSTORE", (Iterator(dstKey, kws.size) ++ kws.iterator.map(_._1) ++ Iterator.single("WEIGHTS") ++ kws.iterator.map(_._2) ++ Iterator("AGGREGATE", aggregate)).toList)(asInt)

  // ZCOUNT
  //
  def zcount(key: Any, min: Double = Double.NegativeInfinity, max: Double = Double.PositiveInfinity, minInclusive: Boolean = true, maxInclusive: Boolean = true)(implicit format: Format): Option[Int] =
    send("ZCOUNT", List(key, Format.formatDouble(min, minInclusive), Format.formatDouble(max, maxInclusive)))(asInt)

}
