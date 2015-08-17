package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class OperationsSpec extends FunSpec 
                     with Matchers
                     with BeforeAndAfterEach
                     with BeforeAndAfterAll {

  val r = new RedisClient("localhost", 6379)

  override def beforeEach = {
  }

  override def afterEach = {
    r.flushdb
  }

  override def afterAll = {
    r.disconnect
  }

  describe("keys") {
    it("should fetch keys") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.keys("anshin*") match {
        case Some(s: List[Option[String]]) => s.size should equal(2)
        case None => fail("should have 2 elements")
      }
    }

    it("should fetch keys with spaces") {
      r.set("anshin 1", "debasish")
      r.set("anshin 2", "maulindu")
      r.keys("anshin*") match {
        case Some(s: List[Option[String]]) => s.size should equal(2)
        case None => fail("should have 2 elements")
      }
    }
  }

  describe("randomkey") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.randomkey match {
        case Some(s: String) => s should startWith("anshin") 
        case None => fail("should have 2 elements")
      }
    }
  }

  describe("rename") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.rename("anshin-2", "anshin-2-new") should equal(true)
      val thrown = the [Exception] thrownBy { r.rename("anshin-2", "anshin-2-new") }
      thrown.getMessage should equal ("ERR no such key")
    }
  }

  describe("renamenx") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.renamenx("anshin-2", "anshin-2-new") should equal(true)
      r.renamenx("anshin-1", "anshin-2-new") should equal(false)
    }
  }

  describe("dbsize") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.dbsize.get should equal(2)
    }
  }

  describe("exists") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.exists("anshin-2") should equal(true)
      r.exists("anshin-1") should equal(true)
      r.exists("anshin-3") should equal(false)
    }
  }

  describe("del") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.del("anshin-2", "anshin-1").get should equal(2)
      r.del("anshin-2", "anshin-1").get should equal(0)
    }
  }

  describe("type") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.getType("anshin-2").get should equal("string")
    }
  }

  describe("expire") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.expire("anshin-2", 1000) should equal(true)
      r.ttl("anshin-2") should equal(Some(1000))
      r.expire("anshin-3", 1000) should equal(false)
    }
  }

  describe("persist") {
    it("should give") {
      r.set("key-2", "maulindu")
      r.expire("key-2", 1000) should equal(true)
      r.ttl("key-2") should equal(Some(1000))
      r.persist("key-2") should equal(true)
      r.ttl("key-2") should equal(Some(-1))
      r.persist("key-3") should equal(false)
    }
  }

  describe("sort") {
    it("should give") {
// sort[A](key:String, limit:Option[Pair[Int, Int]] = None, desc:Boolean = false, alpha:Boolean = false, by:Option[String] = None, get:List[String] = Nil)(implicit format:Format, parse:Parse[A]):Option[List[Option[A]]] = {
      r.hset("hash-1", "description", "one")
      r.hset("hash-1", "order", "100")
      r.hset("hash-2", "description", "two")
      r.hset("hash-2", "order", "25")
      r.hset("hash-3", "description", "three")
      r.hset("hash-3", "order", "50")
      r.sadd("alltest", 1)
      r.sadd("alltest", 2)
      r.sadd("alltest", 3)
      r.sort("alltest").getOrElse(Nil) should equal(List(Some("1"), Some("2"), Some("3")))
      r.sort("alltest", Some((0, 1))).getOrElse(Nil) should equal(List(Some("1")))
      r.sort("alltest", None, true).getOrElse(Nil) should equal(List(Some("3"), Some("2"), Some("1")))
      r.sort("alltest", None, false, false, Some("hash-*->order")).getOrElse(Nil) should equal(List(Some("2"), Some("3"), Some("1")))
      r.sort("alltest", None, false, false, None, List("hash-*->description")).getOrElse(Nil) should equal(List(Some("one"), Some("two"), Some("three")))
      r.sort("alltest", None, false, false, None, List("hash-*->description", "hash-*->order")).getOrElse(Nil) should equal(List(Some("one"), Some("100"), Some("two"), Some("25"), Some("three"), Some("50")))
    }
  }
  import serialization._
  describe("sortNStore") {
    it("should give") {
      r.sadd("alltest", 10)
      r.sadd("alltest", 30)
      r.sadd("alltest", 3)
      r.sadd("alltest", 1)

      // default serialization : return String
      r.sortNStore("alltest", storeAt = "skey").getOrElse(-1) should equal(4)
      r.lrange("skey", 0, 10).get should equal(List(Some("1"), Some("3"), Some("10"), Some("30")))

      // Long serialization : return Long
      implicit val parseLong = Parse[Long](new String(_).toLong)
      r.sortNStore[Long]("alltest", storeAt = "skey").getOrElse(-1) should equal(4)
      r.lrange("skey", 0, 10).get should equal(List(Some(1), Some(3), Some(10), Some(30)))
    }
  }

  describe("ping") {
    it("should return pong") {
      r.ping.get should equal("PONG")
    }
  }

  describe("getConfig") {
    it("should return port") {
      r.getConfig("port").get should equal(Map("port" -> Some("6379")))
    }
  }

  describe("setConfig") {
    it("should set config") {
      r.setConfig("loglevel", "debug").get should equal("OK")
    }
  }

  describe("select") {
    it("should keep selected database when reconnect") {
      val key = new scala.util.Random().nextString(10)
      r.select(1)
      r.set(key, "debasish")
      r.get(key) should equal(Some("debasish"))
      r.reconnect
      r.get(key) should equal(Some("debasish"))
    }
  }
}
