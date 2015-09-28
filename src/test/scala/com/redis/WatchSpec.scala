package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class WatchSpec extends FunSpec 
                     with Matchers
                     with BeforeAndAfterEach
                     with BeforeAndAfterAll {

  implicit val clients = new RedisClientPool("localhost", Some(6379))

  override def beforeEach = {
  }

  override def afterEach = clients.withClient{
    client => client.flushdb
  }

  override def afterAll = {
    clients.withClient{ client => client.disconnect }
    clients.close
  }

  describe("watch") {
    it("should fail a transaction if modified from another client") {
      implicit val clients = new RedisClientPool("localhost", Some(6379))
      class P1 extends Runnable {
        def run() {
          clients.withClient { client =>
            client.watch("key")
            client.pipeline { p =>
              p.set("key", "debasish")
              Thread.sleep(50)
              p.get("key")
              p.get("key1")
            } should equal(None)
          }
        }
      }
      class P2 extends Runnable {
        def run() {
          clients.withClient { client =>
            Thread.sleep(10)
            client.set("key", "anshin")
          }
        }
      }
      new Thread(new P1()).start()
      new Thread(new P2()).start()
    }
  }
}

