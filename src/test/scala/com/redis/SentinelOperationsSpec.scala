package com.redis

import org.scalatest.{OptionValues, BeforeAndAfterAll, BeforeAndAfterEach, FunSpec}
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SentinelOperationsSpec extends FunSpec
  with ShouldMatchers
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with OptionValues {

  val hosts = List(("localhost", 6379), ("localhost", 6380), ("localhost", 6381), ("localhost", 6382))
  val sentinels = List(("localhost", 26379), ("localhost", 26380), ("localhost", 26381), ("localhost", 26382))
  val hostClients = hosts map Function.tupled(new RedisClient(_, _))
  val sentinelClients = sentinels map Function.tupled(new SentinelClient(_, _))

  override def beforeAll() {
    hostClients.foreach { client =>
      if (client.port != 6379) {
        client.slaveof("localhost", 6379)
      }
    }

    Thread sleep 10000 // wait for all the syncs to complete
  }

  override def afterAll() {
    hostClients.foreach (_.slaveof())

    Thread sleep 10000
  }

  describe("masters") {
    it("should return all masters") {
      sentinelClients.foreach { client =>
        val masters = client.masters match {
          case Some(m) => m.collect {
            case Some(details) => (details("name"), details("ip"), details("port"))
          }
          case _ => Nil
        }

        masters should equal (List(("scala-redis-test", "127.0.0.1", "6379")))
      }
    }
  }

  describe("slaves") {
    it("should return all slaves") {
      sentinelClients.foreach { client =>
        val slaves = (client.slaves("scala-redis-test") match {
          case Some(s) => s.collect {
            case Some(details) => (details("ip"), details("port").toInt)
          }
          case _ => Nil
        }).sorted

        slaves should equal (List(("127.0.0.1", 6380), ("127.0.0.1", 6381), ("127.0.0.1", 6382)))
      }
    }
  }

  describe("is-master-down-by-addr") {
    it("should report master is not down") {
      sentinelClients.foreach { client =>
        client.isMasterDownByAddr("127.0.0.1", 6379).get._1 should equal (false)
      }
    }
  }

  describe("get-master-addr-by-name") {
    it("should return master address") {
      sentinelClients.foreach { client =>
        client.getMasterAddrByName("scala-redis-test") should equal (Some(("127.0.0.1", 6379)))
      }
    }
  }

  describe("reset") {
    it("should reset one master") {
      sentinelClients.foreach { client =>
        client.reset("scala-redis-*") should equal (Some(1))
      }

      Thread sleep 10000 // wait for sentinels to pick up slaves again
    }
  }

  describe("failover") {
    it("should automatically update master ip in client") {
      val masterAddr = new SentinelMonitoredMasterAddress(sentinels, "scala-redis-test")
      val master = new RedisClient(masterAddr)

      val oldPort = master.port

      sentinelClients(0).failover("scala-redis-test")
      Thread sleep 30000

      master.port should not equal oldPort

      masterAddr.stopMonitoring()
    }

    it("should automatically update master ip in pool") {
      val masterAddr = new SentinelMonitoredMasterAddress(sentinels, "scala-redis-test")
      val pool = new RedisClientPool(masterAddr)

      var oldPort = -1
      pool.withClient { client =>
        oldPort = client.port
      }
      oldPort should not equal -1

      sentinelClients(0).failover("scala-redis-test")
      Thread sleep 30000

      pool.withClient { client =>
        client.port should not equal oldPort
      }

      masterAddr.stopMonitoring()
    }
  }
}
