package com.github.dnvriend.stream
package io

import akka.Done

import scala.util.Try

final case class MessageDigestResult(messageDigest: MessageDigest, status: Try[Done])