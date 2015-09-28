package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class HashOperationsSpec extends FunSpec 
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
  
  describe("hset") {
    it("should set and get fields") {
      r.hset("hash1", "field1", "val")
      r.hget("hash1", "field1") should be(Some("val"))
    }
    
    it("should set and get maps") {
      r.hmset("hash2", Map("field1" -> "val1", "field2" -> "val2"))
      r.hmget("hash2", "field1") should be(Some(Map("field1" -> "val1")))
      r.hmget("hash2", "field1", "field2") should be(Some(Map("field1" -> "val1", "field2" -> "val2")))
      r.hmget("hash2", "field1", "field2", "field3") should be(Some(Map("field1" -> "val1", "field2" -> "val2")))
    }
    
    it("should increment map values") {
      r.hincrby("hash3", "field1", 1)
      r.hget("hash3", "field1") should be(Some("1"))
    }
    
    it("should check existence") {
      r.hset("hash4", "field1", "val")
      r.hexists("hash4", "field1") should equal(true)
      r.hexists("hash4", "field2") should equal(false)
    }
    
    it("should delete fields") {
      r.hset("hash5", "field1", "val")
      r.hexists("hash5", "field1") should equal(true)
      r.hdel("hash5", "field1") should equal(Some(1))
      r.hexists("hash5", "field1") should equal(false)
      r.hmset("hash5", Map("field1" -> "val1", "field2" -> "val2"))
      r.hdel("hash5", "field1", "field2") should equal(Some(2))
    }
    
    it("should return the length of the fields") {
      r.hmset("hash6", Map("field1" -> "val1", "field2" -> "val2"))
      r.hlen("hash6") should be(Some(2))
    }
    
    it("should return the aggregates") {
      r.hmset("hash7", Map("field1" -> "val1", "field2" -> "val2"))
      r.hkeys("hash7") should be(Some(List("field1", "field2")))
      r.hvals("hash7") should be(Some(List("val1", "val2")))
      r.hgetall("hash7") should be(Some(Map("field1" -> "val1", "field2" -> "val2")))
    }

    it("should increment map values by floats") {
      r.hset("hash1", "field1", 10.50f)
      r.hincrbyfloat("hash1", "field1", 0.1f) should be(Some(10.6f))
      r.hset("hash1", "field1", 5.0e3f)
      r.hincrbyfloat("hash1", "field1", 2.0e2f) should be(Some(5200f))
      r.hset("hash1", "field1", "abc")
      val thrown = the [Exception] thrownBy { r.hincrbyfloat("hash1", "field1", 2.0e2f) }
      thrown.getMessage should include("hash value is not a valid float")
    }
  }
}
