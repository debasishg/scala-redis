package com.redis.ds

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class BlockingDequeSpec extends FunSpec 
                with ShouldMatchers
                with BeforeAndAfterEach
                with BeforeAndAfterAll {

  describe("blocking poll") {
    it("should pull out first element") {

      val r1 = new RedisDequeClient("localhost", 6379).getDeque("btd", blocking = true, timeoutInSecs = 30)
      val r2 = new RedisDequeClient("localhost", 6379).getDeque("btd", blocking = true, timeoutInSecs = 30)

      class Foo extends Runnable {
        def start () {
          val myThread = new Thread(this) ;
          myThread.start() ;
        }

        def run {
          val v = r1.poll
          v.get should equal("foo")
          r1.clear
          r1.disconnect
          r2.disconnect
        }
      }
      (new Foo).start
      r2.size should equal(0)
      r2.addFirst("foo")
    }
  }

  describe("blocking poll with pollLast") {
    it("should pull out first element") {

      val r1 = new RedisDequeClient("localhost", 6379).getDeque("btd", blocking = true, timeoutInSecs = 30)
      val r2 = new RedisDequeClient("localhost", 6379).getDeque("btd", blocking = true, timeoutInSecs = 30)

      class Foo extends Runnable {
        def start () {
          val myThread = new Thread(this) ;
          myThread.start() ;
        }

        def run {
          val v = r1.pollLast
          v.get should equal("foo")
          r1.clear
          r1.disconnect
          r2.disconnect
        }
      }
      (new Foo).start
      r2.size should equal(0)
      r2.addFirst("foo")
    }
  }
}
