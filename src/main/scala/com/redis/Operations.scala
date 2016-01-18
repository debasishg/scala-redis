package com.redis

import serialization._

trait Operations {
  self: Redis =>
  // SORT
  // sort keys in a set, and optionally pull values for them
  def sort[A](key: String,
              limit: Option[(Int, Int)] = None,
              desc: Boolean = false,
              alpha: Boolean = false,
              by: Option[String] = None,
              get: List[String] = Nil)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] = {

    val commands: List[Any] = makeSortArgs(key, limit, desc, alpha, by, get)
    send("SORT", commands)(asList)
  }

  private def makeSortArgs(key: String,
                           limit: Option[(Int, Int)] = None,
                           desc: Boolean = false,
                           alpha: Boolean = false,
                           by: Option[String] = None,
                           get: List[String] = Nil): List[Any] = {
    List(List(key), limit.map(l => List("LIMIT", l._1, l._2)).getOrElse(Nil)
      , (if (desc) List("DESC") else Nil)
      , (if (alpha) List("ALPHA") else Nil)
      , by.map(b => List("BY", b)).getOrElse(Nil)
      , get.map(g => List("GET", g)).flatMap(x => x)
    ).flatMap(x => x)
  }

  // SORT with STORE
  // sort keys in a set, and store result in the supplied key
  def sortNStore[A](key: String,
                    limit: Option[(Int, Int)] = None,
                    desc: Boolean = false,
                    alpha: Boolean = false,
                    by: Option[String] = None,
                    get: List[String] = Nil,
                    storeAt: String)(implicit format: Format, parse: Parse[A]): Option[Long] = {

    val commands = makeSortArgs(key, limit, desc, alpha, by, get) ::: List("STORE", storeAt)
    send("SORT", commands)(asLong)
  }

  // KEYS
  // returns all the keys matching the glob-style pattern.
  def keys[A](pattern: Any = "*")(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("KEYS", List(pattern))(asList)

  // RANDKEY
  // return a randomly selected key from the currently selected DB.
  @deprecated("use randomkey", "2.8") def randkey[A](implicit parse: Parse[A]): Option[A] =
    send("RANDOMKEY")(asBulk)

  // RANDOMKEY
  // return a randomly selected key from the currently selected DB.
  def randomkey[A](implicit parse: Parse[A]): Option[A] =
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
  def dbsize: Option[Long] =
    send("DBSIZE")(asLong)

  // EXISTS (key)
  // test if the specified key exists.
  def exists(key: Any)(implicit format: Format): Boolean =
    send("EXISTS", List(key))(asBoolean)

  // DELETE (key1 key2 ..)
  // deletes the specified keys.
  def del(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    send("DEL", key :: keys.toList)(asLong)

  // TYPE (key)
  // return the type of the value stored at key in form of a string.
  def getType(key: Any)(implicit format: Format): Option[String] =
    send("TYPE", List(key))(asString)

  // EXPIRE (key, expiry)
  // sets the expire time (in sec.) for the specified key.
  def expire(key: Any, ttl: Int)(implicit format: Format): Boolean =
    send("EXPIRE", List(key, ttl))(asBoolean)

  // PEXPIRE (key, expiry)
  // sets the expire time (in milli sec.) for the specified key.
  def pexpire(key: Any, ttlInMillis: Int)(implicit format: Format): Boolean =
    send("PEXPIRE", List(key, ttlInMillis))(asBoolean)

  // EXPIREAT (key, unix timestamp)
  // sets the expire time for the specified key.
  def expireat(key: Any, timestamp: Long)(implicit format: Format): Boolean =
    send("EXPIREAT", List(key, timestamp))(asBoolean)

  // PEXPIREAT (key, unix timestamp)
  // sets the expire timestamp in millis for the specified key.
  def pexpireat(key: Any, timestampInMillis: Long)(implicit format: Format): Boolean =
    send("PEXPIREAT", List(key, timestampInMillis))(asBoolean)

  // TTL (key)
  // returns the remaining time to live of a key that has a timeout
  def ttl(key: Any)(implicit format: Format): Option[Long] =
    send("TTL", List(key))(asLong)

  // PTTL (key)
  // returns the remaining time to live of a key that has a timeout in millis
  def pttl(key: Any)(implicit format: Format): Option[Long] =
    send("PTTL", List(key))(asLong)

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

  // PERSIST (key)
  // Remove the existing timeout on key, turning the key from volatile (a key with an expire set) 
  // to persistent (a key that will never expire as no timeout is associated).
  def persist(key: Any)(implicit format: Format): Boolean =
    send("PERSIST", List(key))(asBoolean)

  // SCAN
  // Incrementally iterate the keys space (since 2.8)
  def scan[A](cursor: Int, pattern: Any = "*", count: Int = 10)(implicit format: Format, parse: Parse[A]): Option[(Option[Int], Option[List[Option[A]]])] =
    send("SCAN", cursor :: ((x: List[Any]) => if (pattern == "*") x else "match" :: pattern :: x)(if (count == 10) Nil else List("count", count)))(asPair)

  // PING
  def ping: Option[String] = send("PING")(asString)

  // WATCH (key1 key2 ..)
  // Marks the given keys to be watched for conditional execution of a transaction.
  // 
  def watch(key: Any, keys: Any*)(implicit format: Format): Boolean =
    send("WATCH", key :: keys.toList)(asBoolean)

  // UNWATCH
  // Flushes all the previously watched keys for a transaction
  def unwatch(): Boolean =
    send("UNWATCH")(asBoolean)

  // CONFIG GET
  def getConfig(key: Any = "*")(implicit format: Format): Option[Map[String, Option[String]]] =
    send("CONFIG", List("GET", key))(asList).map { ls =>
      ls.grouped(2).collect { case Some(k) :: v :: Nil => k -> v }.toMap
    }

  // CONFIG SET
  def setConfig(key: Any, value: Any)(implicit format: Format): Option[String] =
    send("CONFIG", List("SET", key, value))(asString)
}