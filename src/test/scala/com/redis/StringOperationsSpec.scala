package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class StringOperationsSpec extends FunSpec
with Matchers
with BeforeAndAfterEach
with BeforeAndAfterAll {

  val r = new RedisClient("localhost", Some(6379))

  override def beforeEach = {
  }

  override def afterEach = {
    r.flushdb
  }

  override def afterAll = {
    r.disconnect
  }

  describe("set") {
    it("should set key/value pairs") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
    }
  }

  describe("set if not exist") {
    it("should set key/value pairs with exclusiveness and expire") {
      r.set("amit-1", "mor", "nx","ex",6)
      r.get("amit-1") match {
        case Some(s: String) => s should equal("mor")
        case None => fail("should return mor")
      }
      r.del("amit-1")
    }
  }

  describe("set if exists or not") {
    it("should set key/value pairs with exclusiveness and expire") {
      r.set("amit-1", "mor", false, Seconds(6))
      r.get("amit-1") match {
        case Some(s: String) => s should equal("mor")
        case None => fail("should return mor")
      }
      Thread.sleep(6000)
      r.get("amit-1") should equal(None)
      r.del("amit-1")
    }
  }

  describe("fail to set if doesn't exist; succeed later because key doesn't exist; success later because key exists") {
    it("should fail to set key/value pairs with exclusiveness and expire") {
      r.del("amit-1")
      // first trying to set with 'xx' should fail since there is not key present
      // r.set("amit-1", "mor", "xx","ex",6)
      r.set("amit-1", "mor", true, Seconds(6))
      r.get("amit-1") match {
        case Some(s: String) => fail("should return None")
        case None =>
      }
      // second, we set if there is no key and we should succeed
      // r.set("amit-1", "mor", "nx","ex",6)
      r.set("amit-1", "mor", false, Seconds(6))
      r.get("amit-1") match {
        case Some(s: String) => s should equal("mor")
        case None => fail("should return mor")
      }

      // third, since the key is now present (if second succeeded), this would succeed too
      // r.set("amit-1", "mor", "xx","ex",6)
      r.set("amit-1", "mor", true, Seconds(6))
      r.get("amit-1") match {
        case Some(s: String) => s should equal("mor")
        case None => fail("should return mor")
      }

    }
  }

  describe("get") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
    }
    it("should fail for non-existent keys") {
      r.get("anshin-2") match {
        case Some(s: String) => fail("should return None")
        case None =>
      }
    }
  }

  describe("getset") {
    it("should set new values and return old values") {
      r.set("anshin-1", "debasish") should equal(true)
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
      r.getset("anshin-1", "maulindu") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("maulindu")
        case None => fail("should return maulindu")
      }
    }
  }

  describe("setnx") {
    it("should set only if the key does not exist") {
      r.set("anshin-1", "debasish") should equal(true)
      r.setnx("anshin-1", "maulindu") should equal(false)
      r.setnx("anshin-2", "maulindu") should equal(true)
    }
  }

  describe("setex") {
    it("should set values with expiry") {
      val key = "setex-1"
      val value = "value"
      r.setex(key, 1, value) should equal(true)
      r.get(key) match {
        case Some(s:String) => s should equal(value)
        case None => fail("should return value")
      }
      Thread.sleep(2000)
      r.get(key) match {
        case Some(_) => fail("key-1 should have expired")
        case None =>
      }
    }
  }

  describe("incr") {
    it("should increment by 1 for a key that contains a number") {
      r.set("anshin-1", "10") should equal(true)
      r.incr("anshin-1") should equal(Some(11))
    }
    it("should reset to 0 and then increment by 1 for a key that contains a diff type") {
      r.set("anshin-2", "debasish") should equal(true)
      try {
        r.incr("anshin-2")
      } catch { case ex: Throwable => ex.getMessage should startWith("ERR value is not an integer") }
    }
    it("should increment by 5 for a key that contains a number") {
      r.set("anshin-3", "10") should equal(true)
      r.incrby("anshin-3", 5) should equal(Some(15))
    }
    it("should reset to 0 and then increment by 5 for a key that contains a diff type") {
      r.set("anshin-4", "debasish") should equal(true)
      try {
        r.incrby("anshin-4", 5)
      } catch { case ex: Throwable => ex.getMessage should startWith("ERR value is not an integer") }
    }
  }

  describe("incrbyfloat") {
    it("should increment values by floats") {
      r.set("k1", 10.50f)
      r.incrbyfloat("k1", 0.1f) should be(Some(10.6f))
      r.set("k1", 5.0e3f)
      r.incrbyfloat("k1", 2.0e2f) should be(Some(5200f))
      r.set("k1", "abc")
      val thrown = the [Exception] thrownBy { r.incrbyfloat("k1", 2.0e2f) }
      thrown.getMessage should include("value is not a valid float")
    }
  }

  describe("decr") {
    it("should decrement by 1 for a key that contains a number") {
      r.set("anshin-1", "10") should equal(true)
      r.decr("anshin-1") should equal(Some(9))
    }
    it("should reset to 0 and then decrement by 1 for a key that contains a diff type") {
      r.set("anshin-2", "debasish") should equal(true)
      try {
        r.decr("anshin-2")
      } catch { case ex: Throwable => ex.getMessage should startWith("ERR value is not an integer") }
    }
    it("should decrement by 5 for a key that contains a number") {
      r.set("anshin-3", "10") should equal(true)
      r.decrby("anshin-3", 5) should equal(Some(5))
    }
    it("should reset to 0 and then decrement by 5 for a key that contains a diff type") {
      r.set("anshin-4", "debasish") should equal(true)
      try {
        r.decrby("anshin-4", 5)
      } catch { case ex: Throwable => ex.getMessage should startWith("ERR value is not an integer") }
    }
  }

  describe("mget") {
    it("should get values for existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
      r.set("anshin-3", "nilanjan") should equal(true)
      r.mget("anshin-1", "anshin-2", "anshin-3").get should equal(List(Some("debasish"), Some("maulindu"), Some("nilanjan")))
    }
    it("should give None for non-existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
      r.mget("anshin-1", "anshin-2", "anshin-4").get should equal(List(Some("debasish"), Some("maulindu"), None))
    }
  }

  describe("mset") {
    it("should set all keys irrespective of whether they exist") {
      r.mset(
        ("anshin-1", "debasish"),
        ("anshin-2", "maulindu"),
        ("anshin-3", "nilanjan")) should equal(true)
    }

    it("should set all keys only if none of them exist") {
      r.msetnx(
        ("anshin-4", "debasish"),
        ("anshin-5", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(true)
      r.msetnx(
        ("anshin-7", "debasish"),
        ("anshin-8", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(false)
      r.msetnx(
        ("anshin-4", "debasish"),
        ("anshin-5", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(false)
    }
  }

  describe("get with spaces in keys") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin software", "debasish ghosh") should equal(true)
      r.get("anshin software") match {
        case Some(s: String) => s should equal("debasish ghosh")
        case None => fail("should return debasish ghosh")
      }

      r.set("test key with spaces", "I am a value with spaces")
      r.get("test key with spaces").get should equal("I am a value with spaces")
    }
  }

  describe("get with newline values") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin-x", "debasish\nghosh\nfather") should equal(true)
      r.get("anshin-x") match {
        case Some(s: String) => s should equal("debasish\nghosh\nfather")
        case None => fail("should return debasish")
      }
    }
  }

  describe("setrange") {
    it("should set value starting from offset") {
      r.set("key1", "hello world")
      r.setrange("key1", 6, "redis")
      r.get("key1") should equal(Some("hello redis"))

      r.setrange("key2", 6, "redis") should equal(Some(11))
      r.get("key2").get.trim should equal("redis")
      r.get("key2").get.length should equal(11)   // zero padding
    }
  }

  describe("getrange") {
    it("should get value starting from start") {
      r.set("mykey", "This is a string")
      r.getrange[String]("mykey", 0, 3) should equal(Some("This"))
      r.getrange[String]("mykey", -3, -1) should equal(Some("ing"))
      r.getrange[String]("mykey", 0, -1) should equal(Some("This is a string"))
      r.getrange[String]("mykey", 10, 100) should equal(Some("string"))
    }
  }

  describe("strlen") {
    it("should return the length of the value") {
      r.set("mykey", "Hello World")
      r.strlen("mykey") should equal(Some(11))
      r.strlen("nonexisting") should equal(Some(0))
    }
  }

  describe("append") {
    it("should append value to that of a key") {
      r.exists("mykey") should equal(false)
      r.append("mykey", "Hello") should equal(Some(5))
      r.append("mykey", " World") should equal(Some(11))
      r.get[String]("mykey") should equal(Some("Hello World"))
    }
  }

  describe("setbit") {
    it("should set of clear the bit at offset in the string value stored at the key") {
      r.setbit("mykey", 7, 1) should equal(Some(0))
      r.setbit("mykey", 7, 0) should equal(Some(1))
      String.format("%x", new java.math.BigInteger(r.get("mykey").get.getBytes("UTF-8"))) should equal("0")
    }
  }

  describe("getbit") {
    it("should return the bit value at offset in the string") {
      r.setbit("mykey", 7, 1) should equal(Some(0))
      r.getbit("mykey", 0) should equal(Some(0))
      r.getbit("mykey", 7) should equal(Some(1))
      r.getbit("mykey", 100) should equal(Some(0))
    }
  }

  describe("bitcount") {
    it("should do a population count") {
      r.setbit("mykey", 7, 1)
      r.bitcount("mykey") should equal(Some(1))
      r.setbit("mykey", 8, 1)
      r.bitcount("mykey") should equal(Some(2))
    }
  }

  describe("bitop") {
    it("should apply logical operators to the srckeys and store the results in destKey") {
      // key1: 101
      // key2:  10
      r.setbit("key1", 0, 1)
      r.setbit("key1", 2, 1)
      r.setbit("key2", 1, 1)
      r.bitop("AND", "destKey", "key1", "key2") should equal(Some(1))
      // 101 AND 010 = 000
      (0 to 2).foreach { bit =>
        r.getbit("destKey", bit) should equal(Some(0))
      }

      r.bitop("OR", "destKey", "key1", "key2") should equal(Some(1))
      // 101 OR 010 = 111
      (0 to 2).foreach { bit =>
        r.getbit("destKey", bit) should equal(Some(1))
      }

      r.bitop("NOT", "destKey", "key1") should equal(Some(1))
      r.getbit("destKey", 0) should equal(Some(0))
      r.getbit("destKey", 1) should equal(Some(1))
      r.getbit("destKey", 2) should equal(Some(0))
    }
  }

  /** uncomment to test timeout : need a custom redis.conf
  describe("timeout") {
    it("should append value to that of a key") {
      r.set("mykey", "Hello World")
      r.strlen("mykey") should equal(Some(11))
      r.strlen("nonexisting") should equal(Some(0))
      Thread.sleep(150000)
      r.set("nonexisting", "Hello World")
      r.strlen("nonexisting") should equal(Some(11))
    }
  }
    **/
}
