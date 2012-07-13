package com.redis

import serialization._

trait EvalOperations { self: Redis =>

  // EVAL
  // evaluates lua code on the server.
  def evalMultiBulk[A](luaCode: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("EVAL",  argsForEval(luaCode, keys, args))(asList[A])

  def evalBulk[A](luaCode: String, keys: List[Any], args: List[Any])(implicit format: Format, parse: Parse[A]): Option[A] ={println("args for eval = " + argsForEval(luaCode, keys, args))
    send("EVAL", argsForEval(luaCode, keys, args))(asBulk)}

  private def argsForEval(luaCode: String, keys: List[Any], args: List[Any]): List[Any] =
    luaCode :: keys.length :: keys ::: args

}
