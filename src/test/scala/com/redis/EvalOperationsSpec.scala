package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class EvalOperationsSpec extends FunSpec
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
    
    it("should evalsha lua code hash and return the integer result") {
      import serialization.Parse.Implicits.parseInt
      val sha = r.scriptLoad("return 1").get
      val i: Option[Int] = r.evalSHA(sha, List(), List())
      i should equal (Some(1))
    }
    it("should evalsha lua code hash and return the integer part of a double result") {
      /*
       * see http://redis.io/commands/eval
       * 
       * Lua has a single numerical type, Lua numbers. There is no distinction between integers and floats.
       * So we always convert Lua numbers into integer replies, removing the decimal part of the number if
       * any. If you want to return a float from Lua you should return it as a string, exactly like Redis
       * itself does (see for instance the ZSCORE command).
       */
      import serialization.Parse.Implicits.parseDouble
      val sha = r.scriptLoad("return 1.5").get
      val i: Option[Double] = r.evalSHA(sha, List(), List())
      i should equal (Some(1))
    }
    it("should evalmultisha lua code hash and return the string results") {
      import serialization.Parse.Implicits.parseString
      val sha = r.scriptLoad("return {'1', '2'}").get
      val i: Option[List[Option[String]]] = r.evalMultiSHA(sha, List(), List())
      i should equal (Some(List(Some("1"), Some("2"))))
    }
    it("should evalmultisha lua code hash and return the integer results") {
      import serialization.Parse.Implicits.parseInt
      val sha = r.scriptLoad("return {1, 2}").get
      val i: Option[List[Option[Int]]] = r.evalMultiSHA(sha, List(), List())
      i should equal (Some(List(Some(1), Some(2))))
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
