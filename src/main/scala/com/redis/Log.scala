package com.redis

import org.slf4j.LoggerFactory

trait Log {
 private val log = LoggerFactory.getLogger(getClass)

 def ifTrace(message: => String) = if (log.isTraceEnabled) trace(message)
 def trace(message:String, values:AnyRef*) =
     log.trace(message, values)
 def trace(message:String, error:Throwable) = log.trace(message, error)

 def ifDebug(message: => String) = if (log.isDebugEnabled) debug(message)
 def debug(message:String, values:AnyRef*) =
     log.debug(message, values)
 def debug(message:String, error:Throwable) = log.debug(message, error)

 def ifInfo(message: => String) = if (log.isInfoEnabled) info(message)
 def info(message:String, values:AnyRef*) =
     log.info(message, values)
 def info(message:String, error:Throwable) = log.info(message, error)

 def ifWarn(message: => String) = if (log.isWarnEnabled) warn(message)
 def warn(message:String, values:AnyRef*) =
     log.warn(message, values)
 def warn(message:String, error:Throwable) = log.warn(message, error)

 def ifError(message: => String) = if (log.isErrorEnabled) error(message)
 def error(message:String, values:AnyRef*) =
     log.error(message, values)
 def error(message:String, error:Throwable) = log.error(message, error)
}