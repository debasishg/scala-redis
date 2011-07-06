package com.redis

import org.scalatest.Spec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import serialization._

@RunWith(classOf[JUnitRunner])
class SerializationSpec extends Spec 
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

  it("should not conflict when using all built in parsers") {
    import Parse.Implicits._
    r.hmset("hash", Map("field1" -> "1", "field2" -> 2))
    r.hmget[String,String]("hash", "field1", "field2") should be(Some(Map("field1" -> "1", "field2" -> "2")))
    r.hmget[String,Int]("hash", "field1", "field2") should be(Some(Map("field1" -> 1, "field2" -> 2)))
    r.hmget[String,Int]("hash", "field1", "field2", "field3") should be(Some(Map("field1" -> 1, "field2" -> 2)))
  }

  it("should use a provided implicit parser") {
    r.hmset("hash", Map("field1" -> "1", "field2" -> 2))

    r.hmget("hash", "field1", "field2") should be(Some(Map("field1" -> "1", "field2" -> "2")))

    implicit val parseInt = Parse[Int](new String(_).toInt)

    r.hmget[String,Int]("hash", "field1", "field2") should be(Some(Map("field1" -> 1, "field2" -> 2)))
    r.hmget[String,String]("hash", "field1", "field2") should be(Some(Map("field1" -> "1", "field2" -> "2")))
    r.hmget[String,Int]("hash", "field1", "field2", "field3") should be(Some(Map("field1" -> 1, "field2" -> 2)))
  }

  it("should use a provided implicit string parser") {
    import Parse.Implicits.parseInt
    implicit val parseString = Parse[String](new String(_).toInt.toBinaryString)
    r.hmset("hash", Map("field1" -> "1", "field2" -> 2))
    r.hmget[String,Int]("hash", "field1", "field2") should be(Some(Map("field1" -> 1, "field2" -> 2)))
    r.hmget[String,String]("hash", "field1", "field2") should be(Some(Map("field1" -> "1", "field2" -> "10")))
  }

  it("should use a seperate parser for key/values with Map") {
    r.hmset("hash7", Map("field1" -> 1, "field2" -> 2))
    r.hgetall("hash7") should be(Some(Map("field1" -> "1", "field2" -> "2")))    
    import Parse.Implicits._
    r.hgetall[String,Int]("hash7") should be(Some(Map("field1" -> 1, "field2" -> 2)))
  }


  it("should use a provided implicit formatter") {
    case class Upper(s: String)
    r.hmset("hash1", Map("field1" -> Upper("val1"), "field2" -> Upper("val2")))
    implicit val format = Format{case Upper(s) => s.toUpperCase}
    r.hmset("hash2", Map("field1" -> Upper("val1"), "field2" -> Upper("val2")))
    r.hmget("hash1", "field1", "field2") should be(Some(Map("field1" -> "Upper(val1)", "field2" -> "Upper(val2)")))
    r.hmget("hash2", "field1", "field2") should be(Some(Map("field1" -> "VAL1", "field2" -> "VAL2")))
  }

  it("should parse string as a bytearray with an implicit parser") {
    val x = "debasish".getBytes("UTF-8")
    r.set("key", x)
    import Parse.Implicits.parseByteArray
    val s = r.get[Array[Byte]]("key")
    new String(s.get) should equal("debasish")
    r.get[Array[Byte]]("keey") should equal(None)
  }
}
