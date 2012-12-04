package com.redis.cluster

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.redis.RedisClient
import com.redis.serialization.Format
import collection.mutable.WrappedArray


@RunWith(classOf[JUnitRunner])
class RedisClusterSpec extends FunSpec 
                       with ShouldMatchers
                       with BeforeAndAfterEach
                       with BeforeAndAfterAll {

  val nodes = Array(ClusterNode("node1", "localhost", 6379), ClusterNode("node2", "localhost", 6380), ClusterNode("node3", "localhost", 6381))
  val r = new RedisCluster(new WrappedArray.ofRef(nodes): _*) {
    val keyTag = Some(RegexKeyTag)
  }

  override def beforeEach = {}

  override def afterEach = r.flushdb

  override def afterAll = r.close

  def formattedKey(key: Any)(implicit format: Format) = {
    format(key)
  }

  describe("cluster operations") {
    it("should set") {
      val l = List("debasish", "maulindu", "ramanendu", "nilanjan", "tarun", "tarun", "tarun")

      // last 3 should map to the same node
      l.map(r.nodeForKey(_)).reverse.slice(0, 3).forall(_.toString == "node2") should equal(true)

      // set
      l foreach (s => r.processForKey(s)(_.set(s, "working in anshin")) should equal(true))

      // check get: should return all 5
      r.keys("*").get.size should equal(5)
    }

    it("should get keys from proper nodes") {
      val l = List("debasish", "maulindu", "ramanendu", "nilanjan", "tarun", "tarun", "tarun")

      // set
      l foreach (s =>  r.processForKey(s)(_.set(s, s + " is working in anshin")) should equal(true))

      r.get("debasish").get should equal("debasish is working in anshin")
      r.get("maulindu").get should equal("maulindu is working in anshin")
      l.map(r.get(_).get).distinct.size should equal(5)
    }

    it("should do all operations on the cluster") {
      val l = List("debasish", "maulindu", "ramanendu", "nilanjan", "tarun", "tarun", "tarun")

      // set
      l foreach (s => r.processForKey(s)(_.set(s, s + " is working in anshin")) should equal(true))

      r.dbsize.get should equal(5)
      r.exists("debasish") should equal(true)
      r.exists("maulindu") should equal(true)
      r.exists("debasish-1") should equal(false)

      r.del("debasish", "nilanjan").get should equal(2)
      r.dbsize.get should equal(3)
      r.del("satire").get should equal(0)
    }

    it("mget on a cluster should fetch values in the same order as the keys") {
      val l = List("debasish", "maulindu", "ramanendu", "nilanjan", "tarun", "tarun", "tarun")

      // set
      l foreach (s => r.processForKey(s)(_.set(s, s + " is working in anshin")) should equal(true))

      // mget
      r.mget(l.head, l.tail: _*).get.map(_.get.split(" ")(0)) should equal(l)
    }

    it("list operations should work on the cluster"){
      r.lpush("java-virtual-machine-langs", "java") should equal(Some(1))
      r.lpush("java-virtual-machine-langs", "jruby") should equal(Some(2))
      r.lpush("java-virtual-machine-langs", "groovy") should equal(Some(3))
      r.lpush("java-virtual-machine-langs", "scala") should equal(Some(4))
      r.llen("java-virtual-machine-langs") should equal(Some(4))
    }

    it("keytags should ensure mapping to the same server"){
      r.lpush("java-virtual-machine-{langs}", "java") should equal(Some(1))
      r.lpush("java-virtual-machine-{langs}", "jruby") should equal(Some(2))
      r.lpush("java-virtual-machine-{langs}", "groovy") should equal(Some(3))
      r.lpush("java-virtual-machine-{langs}", "scala") should equal(Some(4))
      r.llen("java-virtual-machine-{langs}") should equal(Some(4))
      r.lpush("microsoft-platform-{langs}", "c++") should equal(Some(1))
      r.rpoplpush("java-virtual-machine-{langs}", "microsoft-platform-{langs}").get should equal("java")
      r.llen("java-virtual-machine-{langs}") should equal(Some(3))
      r.llen("microsoft-platform-{langs}") should equal(Some(2))
    }

    it("replace node should not change hash ring order"){
      val r = new RedisCluster(new WrappedArray.ofRef(nodes): _*) {
		  val keyTag = Some(RegexKeyTag)
	  }
      r.set("testkey1", "testvalue2")
      r.get("testkey1") should equal (Some("testvalue2"))

      val nodename = r.hr.getNode(formattedKey("testkey1")).toString

      //simulate the same value is duplicated to slave
      //for test, don't set to master, just to make sure the expected value is loaded from slave
      val redisClient = new RedisClient("localhost", 6382)
      redisClient.set("testkey1", "testvalue1")

      //replaced master with slave on the same node
      r.replaceServer(ClusterNode(nodename, "localhost", 6382))
      r.nodeForKey("testkey1").port should equal (6382)
      r.hr.cluster.find(_.node.nodename.equals(nodename)).get.port should equal(6382)
      r.get("testkey1") should equal (Some("testvalue1"))

      //switch back to master. the old value is loaded
      val oldnode = nodes.filter(_.nodename.equals(nodename))(0)
      r.replaceServer(oldnode)
      r.get("testkey1") should equal (Some("testvalue2"))
    }
    
    it("remove failure node should change hash ring order so that key on failure node should be served by other running nodes"){
	  val r = new RedisCluster(new WrappedArray.ofRef(nodes): _*) {
		  val keyTag = Some(RegexKeyTag)
	  }
      r.set("testkey1", "testvalue2")
      r.get("testkey1") should equal (Some("testvalue2"))

      val nodename = r.hr.getNode(formattedKey("testkey1")).toString

      //replaced master with slave on the same node
      r.removeServer(nodename)
      r.get("testkey1") should equal (None)

      r.set("testkey1", "testvalue2")
      r.get("testkey1") should equal (Some("testvalue2"))
    }
    
    it("list nodes should return the running nodes but not configured nodes"){
      val r = new RedisCluster(new WrappedArray.ofRef(nodes): _*) {
		  val keyTag = Some(RegexKeyTag)
	  }
      r.listServers.toSet should equal (nodes.toSet)
      r.removeServer("node1")
      r.listServers.toSet should equal (nodes.filterNot(_.nodename.equals("node1")).toSet)
    }
  }
}
