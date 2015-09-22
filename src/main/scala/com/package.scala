package com

package object redis {
  sealed trait SecondsOrMillis
  case class Seconds(value: Long) extends SecondsOrMillis
  case class Millis(value: Long) extends SecondsOrMillis
}
