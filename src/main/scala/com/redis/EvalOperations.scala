package com.redis

import serialization._

trait EvalOperations { self: Redis =>

  // EVAL
  // evaluates lua code on the server.
  def evalMultiBulk[A](luaCode: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("EVAL",  argsForEval(luaCode, keys, args))(asList[A])

  def evalBulk[A](luaCode: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[A] =
    send("EVAL", argsForEval(luaCode, keys, args))(asBulk)
    
  def evalMultiSHA[A](shahash: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("EVALSHA", argsForEval(shahash, keys, args))(asList[A])
    
  def evalSHA[A](shahash: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[A] =
    send("EVALSHA", argsForEval(shahash, keys, args))(asAny.asInstanceOf[Option[A]])
  
  def scriptLoad(luaCode: String): Option[String] = {
    send("SCRIPT", List("LOAD", luaCode))(asBulk)
  }
  
  def scriptExists(shahash: String): Option[Int] = {
    send("SCRIPT", List("EXISTS", shahash))(asList[String]) match {
      case Some(list) => {
        if (list.size>0 && list(0).isDefined){
          Some(list(0).get.toInt)
        }else{
          None
        }
      }
      case None => None
    }
  }
  
  def scriptFlush: Option[String] = {
    send("SCRIPT", List("FLUSH"))(asString)
  }
  
  private def argsForEval(luaCode: String, keys: List[Any], args: List[Any]): List[Any] =
    luaCode :: keys.length :: keys ::: args
}
