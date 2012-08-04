package com.redis

import serialization._

  // SORT
  // sort keys in a set, and optionally pull values for them
trait Operations { self: Redis =>
  def sort[A](key:String, limit:Option[Pair[Int, Int]] = None, desc:Boolean = false, alpha:Boolean = false, by:Option[String] = None, get:List[String] = Nil)(implicit format:Format, parse:Parse[A]):Option[List[Option[A]]] = {
    val commands:List[Any] =
      List(List(key), limit.map(l => List("LIMIT", l._1, l._2)).getOrElse(Nil)
      , (if (desc) List("DESC") else Nil)
      , (if (alpha) List("ALPHA") else Nil)
      , by.map(b => List("BY", b)).getOrElse(Nil)
      , get.map(g => List("GET", g)).flatMap(x=>x)
      ).flatMap(x=>x)
    send("SORT", commands)(asList)
  }
  // KEYS
  // returns all the keys matching the glob-style pattern.
  def keys[A](pattern: Any = "*")(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("KEYS", List(pattern))(asList)

  // RANDKEY
  // return a randomly selected key from the currently selected DB.
  def randkey[A](implicit parse: Parse[A]): Option[A] =
    send("RANDOMKEY")(asBulk)

  // RENAME (oldkey, newkey)
  // atomically renames the key oldkey to newkey.
  def rename(oldkey: Any, newkey: Any)(implicit format: Format): Boolean =
    send("RENAME", List(oldkey, newkey))(asBoolean)
  
  // RENAMENX (oldkey, newkey)
  // rename oldkey into newkey but fails if the destination key newkey already exists.
  def renamenx(oldkey: Any, newkey: Any)(implicit format: Format): Boolean =
    send("RENAMENX", List(oldkey, newkey))(asBoolean)
  
  // DBSIZE
  // return the size of the db.
  def dbsize: Option[Int] =
    send("DBSIZE")(asInt)

  // EXISTS (key)
  // test if the specified key exists.
  def exists(key: Any)(implicit format: Format): Boolean =
    send("EXISTS", List(key))(asBoolean)

  // DELETE (key1 key2 ..)
  // deletes the specified keys.
  def del(key: Any, keys: Any*)(implicit format: Format): Option[Int] =
    send("DEL", key :: keys.toList)(asInt)

  // TYPE (key)
  // return the type of the value stored at key in form of a string.
  def getType(key: Any)(implicit format: Format): Option[String] =
    send("TYPE", List(key))(asString)

  // EXPIRE (key, expiry)
  // sets the expire time (in sec.) for the specified key.
  def expire(key: Any, expiry: Int)(implicit format: Format): Boolean =
    send("EXPIRE", List(key, expiry))(asBoolean)

  // TTL (key)
  // returns the remaining time to live of a key that has a timeout
  def ttl(key: Any)(implicit format: Format): Option[Int] =
    send("TTL", List(key))(asInt)

  // SELECT (index)
  // selects the DB to connect, defaults to 0 (zero).
  def select(index: Int): Boolean =
    send("SELECT", List(index))(asBoolean match {
      case true => {
        db = index
        true
      }
      case _ => false
    })
    
  
  // FLUSHDB the DB
  // removes all the DB data.
  def flushdb: Boolean =
    send("FLUSHDB")(asBoolean)

  // FLUSHALL the DB's
  // removes data from all the DB's.
  def flushall: Boolean =
    send("FLUSHALL")(asBoolean)

  // MOVE
  // Move the specified key from the currently selected DB to the specified destination DB.
  def move(key: Any, db: Int)(implicit format: Format): Boolean =
    send("MOVE", List(key, db))(asBoolean)
  
  // QUIT
  // exits the server.
  def quit: Boolean =
    send("QUIT")(disconnect)
  
  // AUTH
  // auths with the server.
  def auth(secret: Any)(implicit format: Format): Boolean =
    send("AUTH", List(secret))(asBoolean)
}
