package com.redis

import serialization._

trait HashOperations { self: Redis =>
  def hset(key: Any, field: Any, value: Any)(implicit format: Format): Boolean =
    send("HSET", List(key, field, value))(asBoolean)
  
  def hsetnx(key: Any, field: Any, value: Any)(implicit format: Format): Boolean =
    send("HSETNX", List(key, field, value))(asBoolean)
  
  def hget[A](key: Any, field: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("HGET", List(key, field))(asBulk)
  
  def hmset(key: Any, map: Iterable[Product2[Any,Any]])(implicit format: Format): Boolean =
    send("HMSET", key :: flattenPairs(map))(asBoolean)
  
  def hmget[K,V](key: Any, fields: K*)(implicit format: Format, parseV: Parse[V]): Option[Map[K,V]] =
    send("HMGET", key :: fields.toList){
      asList.map { values =>
        fields.zip(values).flatMap {
          case (field,Some(value)) => Some((field,value))
          case (_,None) => None
        }.toMap
      }
    }
  
  def hincrby(key: Any, field: Any, value: Int)(implicit format: Format): Option[Long] =
    send("HINCRBY", List(key, field, value))(asLong)
  
  def hexists(key: Any, field: Any)(implicit format: Format): Boolean =
    send("HEXISTS", List(key, field))(asBoolean)
  
  def hdel(key: Any, field: Any, fields: Any*)(implicit format: Format): Option[Long] =
    send("HDEL", List(key, field) ::: fields.toList)(asLong)
  
  def hlen(key: Any)(implicit format: Format): Option[Long] =
    send("HLEN", List(key))(asLong)
  
  def hkeys[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[List[A]] =
    send("HKEYS", List(key))(asList.map(_.flatten))
  
  def hvals[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[List[A]] =
    send("HVALS", List(key))(asList.map(_.flatten))
  
  def hgetall[K,V](key: Any)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]): Option[Map[K,V]] =
    send("HGETALL", List(key))(asListPairs[K,V].map(_.flatten.toMap))
}
