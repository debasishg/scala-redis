package com.redis

import serialization._

trait SortedSetOperations { self: Redis =>
  
  // ZADD (Variadic: >= 2.4)
  // Add the specified members having the specified score to the sorted set stored at key.
  def zadd(key: Any, score: Double, member: Any, scoreVals: (Double, Any)*)(implicit format: Format): Option[Long] =
    send("ZADD", List(key, score, member) ::: scoreVals.toList.map(x => List(x._1, x._2)).flatten)(asLong)
  
  // ZREM (Variadic: >= 2.4)
  // Remove the specified members from the sorted set value stored at key.
  def zrem(key: Any, member: Any, members: Any*)(implicit format: Format): Option[Long] =
    send("ZREM", List(key, member) ::: members.toList)(asLong)
  
  // ZINCRBY
  // 
  def zincrby(key: Any, incr: Double, member: Any)(implicit format: Format): Option[Double] =
    send("ZINCRBY", List(key, incr, member))(asBulk(Parse.Implicits.parseDouble))
  
  // ZCARD
  // 
  def zcard(key: Any)(implicit format: Format): Option[Long] =
    send("ZCARD", List(key))(asLong)
  
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
          limit: Option[(Int, Int)],
          sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]): Option[List[A]] = {

    val (limitEntries, minParam, maxParam) = 
      zrangebyScoreWithScoreInternal(min, minInclusive, max, maxInclusive, limit)

    val params = sortAs match {
      case ASC => ("ZRANGEBYSCORE", key :: minParam :: maxParam :: limitEntries)
      case DESC => ("ZREVRANGEBYSCORE", key :: maxParam :: minParam :: limitEntries)
    }
    send(params._1, params._2)(asList.map(_.flatten))
  }

  def zrangebyscoreWithScore[A](key: Any,
          min: Double = Double.NegativeInfinity,
          minInclusive: Boolean = true,
          max: Double = Double.PositiveInfinity,
          maxInclusive: Boolean = true,
          limit: Option[(Int, Int)],
          sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]): Option[List[(A, Double)]] = {

    val (limitEntries, minParam, maxParam) = 
      zrangebyScoreWithScoreInternal(min, minInclusive, max, maxInclusive, limit)

    val params = sortAs match {
      case ASC => ("ZRANGEBYSCORE", key :: minParam :: maxParam :: "WITHSCORES" :: limitEntries)
      case DESC => ("ZREVRANGEBYSCORE", key :: maxParam :: minParam :: "WITHSCORES" :: limitEntries)
    }
    send(params._1, params._2)(asListPairs(parse, Parse.Implicits.parseDouble).map(_.flatten))
  }

  private def zrangebyScoreWithScoreInternal[A](
          min: Double = Double.NegativeInfinity,
          minInclusive: Boolean = true,
          max: Double = Double.PositiveInfinity,
          maxInclusive: Boolean = true,
          limit: Option[(Int, Int)])
          (implicit format: Format, parse: Parse[A]): (List[Any], String, String) = {

    val limitEntries = 
      if(!limit.isEmpty) { 
        "LIMIT" :: limit.toList.flatMap(l => List(l._1, l._2))
      } else { 
        List()
      }

    val minParam = Format.formatDouble(min, minInclusive)
    val maxParam = Format.formatDouble(max, maxInclusive)
    (limitEntries, minParam, maxParam)
  }

  // ZRANK
  // ZREVRANK
  //
  def zrank(key: Any, member: Any, reverse: Boolean = false)(implicit format: Format): Option[Long] =
    send(if (reverse) "ZREVRANK" else "ZRANK", List(key, member))(asLong)

  // ZREMRANGEBYRANK
  //
  def zremrangebyrank(key: Any, start: Int = 0, end: Int = -1)(implicit format: Format): Option[Long] =
    send("ZREMRANGEBYRANK", List(key, start, end))(asLong)

  // ZREMRANGEBYSCORE
  //
  def zremrangebyscore(key: Any, start: Double = Double.NegativeInfinity, end: Double = Double.PositiveInfinity)(implicit format: Format): Option[Long] =
    send("ZREMRANGEBYSCORE", List(key, start, end))(asLong)

  // ZUNION
  //
  def zunionstore(dstKey: Any, keys: Iterable[Any], aggregate: Aggregate = SUM)(implicit format: Format): Option[Long] =
    send("ZUNIONSTORE", (Iterator(dstKey, keys.size) ++ keys.iterator ++ Iterator("AGGREGATE", aggregate)).toList)(asLong)

  def zunionstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], aggregate: Aggregate = SUM)(implicit format: Format): Option[Long] =
    send("ZUNIONSTORE", (Iterator(dstKey, kws.size) ++ kws.iterator.map(_._1) ++ Iterator.single("WEIGHTS") ++ kws.iterator.map(_._2) ++ Iterator("AGGREGATE", aggregate)).toList)(asLong)

  // ZINTERSTORE
  //
  def zinterstore(dstKey: Any, keys: Iterable[Any], aggregate: Aggregate = SUM)(implicit format: Format): Option[Long] =
    send("ZINTERSTORE", (Iterator(dstKey, keys.size) ++ keys.iterator ++ Iterator("AGGREGATE", aggregate)).toList)(asLong)

  def zinterstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], aggregate: Aggregate = SUM)(implicit format: Format): Option[Long] =
    send("ZINTERSTORE", (Iterator(dstKey, kws.size) ++ kws.iterator.map(_._1) ++ Iterator.single("WEIGHTS") ++ kws.iterator.map(_._2) ++ Iterator("AGGREGATE", aggregate)).toList)(asLong)

  // ZCOUNT
  //
  def zcount(key: Any, min: Double = Double.NegativeInfinity, max: Double = Double.PositiveInfinity, minInclusive: Boolean = true, maxInclusive: Boolean = true)(implicit format: Format): Option[Long] =
    send("ZCOUNT", List(key, Format.formatDouble(min, minInclusive), Format.formatDouble(max, maxInclusive)))(asLong)

  // ZSCAN
  // Incrementally iterate sorted sets elements and associated scores (since 2.8)
  def zscan[A](key: Any, cursor: Int, pattern: Any = "*", count: Int = 10)(implicit format: Format, parse: Parse[A]): Option[(Option[Int], Option[List[Option[A]]])] =
    send("ZSCAN", key :: cursor :: ((x: List[Any]) => if(pattern == "*") x else "match" :: pattern :: x)(if(count == 10) Nil else List("count", count)))(asPair)

  // ZRANGEBYLEX
  def zrangebylex[A](key: Any,
                     min: String = "-",
                     minInclusive: Boolean = true,
                     max: String = "+",
                     maxInclusive: Boolean = true,
                     limit: Option[(Int, Int)] = None)
                    (implicit format: Format, parse: Parse[A]): Option[List[A]] = {
    val minParam: String = Format.formatString(min, minInclusive)
    val maxParam: String = Format.formatString(max, maxInclusive)
    val limitEntries =
      if (!limit.isEmpty) {
        "LIMIT" :: limit.toList.flatMap(l => List(l._1, l._2))
      } else {
        List()
      }
    send("ZRANGEBYLEX", List(key, minParam, maxParam) ::: limitEntries)(asList.map(_.flatten))
  }
}
