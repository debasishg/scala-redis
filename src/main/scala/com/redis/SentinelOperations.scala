package com.redis

import serialization._

trait SentinelOperations { self: Redis =>

  def masters[K,V](implicit format: Format, parseK: Parse[K], parseV: Parse[V]): Option[List[Option[Map[K,V]]]] =
    send("SENTINEL", List("MASTERS"))(asListOfListPairs[K,V].map(_.map(_.map(_.flatten.toMap))))

  def slaves[K,V](name: String)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]):
      Option[List[Option[Map[K,V]]]] =
    send("SENTINEL", List("SLAVES", name))(asListOfListPairs[K,V].map(_.map(_.map(_.flatten.toMap))))

  def isMasterDownByAddr(host: String, port: Int): Option[(Boolean, String)] =
    send("SENTINEL", List("IS-MASTER-DOWN-BY-ADDR", host, port))(asList) match {
      case Some(List(Some(down), Some(leader))) => Some(down.toInt == 1, leader)
      case _ => None
    }

  def getMasterAddrByName(name: String): Option[(String, Int)] =
    send("SENTINEL", List("GET-MASTER-ADDR-BY-NAME", name))(asList[String]) match {
      case Some(List(Some(h), Some(p))) => Some(h, p.toInt)
      case _ => None
    }

  def reset(pattern: String): Option[Int] =
    send("SENTINEL", List("RESET", pattern))(asInt)

  def failover(name: String): Boolean =
    send("SENTINEL", List("FAILOVER", name))(asBoolean)

}
