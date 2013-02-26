package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class EvalOperationsSpec extends FunSpec
                     with ShouldMatchers
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

  describe("eval") {
    it("should eval lua code and get a string reply") {
      r.evalBulk[String]("return 'val1';", List(), List()) should be(Some("val1"))
    }

    it("should eval lua code and get a string array reply") {
      r.evalMultiBulk[String]("return { 'val1','val2' };", List(), List()) should be(Some(List(Some("val1"), Some("val2"))))
    }

    it("should eval lua code and get a string array reply from its arguments") {
      r.evalMultiBulk[String]("return { ARGV[1],ARGV[2] };", List(), List("a", "b")) should be(Some(List(Some("a"), Some("b"))))
    }

    it("should eval lua code and get a string array reply from its arguments & keys") {
      r.set("a", "a")
      r.set("a", "a")
      r.evalMultiBulk[String]("return { KEYS[1],KEYS[2],ARGV[1],ARGV[2] };", List("a", "b"), List("a", "b")) should be(Some(List(Some("a"), Some("b"), Some("a"), Some("b"))))
    }

    it("should eval lua code and get a string reply when passing keys") {
      r.set("a", "b")
      r.evalBulk[String]("return redis.call('get', KEYS[1]);", List("a"), List()) should be(Some("b"))
    }

    it("should eval lua code and get a string array reply when passing keys") {
      r.lpush("z", "a")
      r.lpush("z", "b")
      r.evalMultiBulk[String]("return redis.call('lrange', KEYS[1], 0, 1);", List("z"), List()) should be(Some(List(Some("b"), Some("a"))))
    }
    
    it("should evalsha lua code hash and execute script when passing keys") {
      val setname = "records";
      
      val luaCode = """
	        local res = redis.call('ZRANGEBYSCORE', KEYS[1], 0, 100, 'WITHSCORES')
	        return res
	        """
      val shahash = r.scriptLoad(luaCode)
      
      r.zadd(setname, 10, "mmd")
      r.zadd(setname, 22, "mmc")
      r.zadd(setname, 12.5, "mma")
      r.zadd(setname, 14, "mem")
      
      val rs = r.evalMultiSHA[String](shahash.get, List("records"), List())
      rs should equal (Some(List(Some("mmd"), Some("10"), Some("mma"), Some("12.5"), Some("mem"), Some("14"), Some("mmc"), Some("22"))))
    }
    
    it("should check if script exists when passing its sha hash code") {      
      val luaCode = """
	        local res = redis.call('ZRANGEBYSCORE', KEYS[1], 0, 100, 'WITHSCORES')
	        return res
	        """
      val shahash = r.scriptLoad(luaCode)
      
      val rs = r.scriptExists(shahash.get)
      rs should equal (Some(1))
    }
    
    it("should remove script cache") {      
      val luaCode = """
	        local res = redis.call('ZRANGEBYSCORE', KEYS[1], 0, 100, 'WITHSCORES')
	        return res
	        """
      val shahash = r.scriptLoad(luaCode)
      
      r.scriptFlush should equal (Some("OK"))
      
      r.scriptExists(shahash.get) should equal (Some(0))
    }
  }
}
