/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend.activemq

import java.sql.{ ResultSet, Statement }

import akka.actor.ActorSystem
import slick.driver.PostgresDriver.api._

import scala.annotation.tailrec

trait DatabaseResources {

  def system: ActorSystem

  def withDatabase[A](f: Database ⇒ A): A =
    f(Database(system).db)

  def withSession[A](f: Session ⇒ A): A = {
    withDatabase { db ⇒
      val session = db.createSession()
      try f(session) finally session.close()
    }
  }

  def withStatement[A](f: Statement ⇒ A): A =
    withSession(session ⇒ session.withStatement()(f))

  def tables: List[String] = {
    val selectTablesQuery = "select table_name from information_schema.tables where table_schema = 'public' order by table_name"
    @tailrec
    def loop(rs: ResultSet, acc: List[String]): List[String] =
      if (rs.next) loop(rs, rs.getString("table_name") +: acc) else acc
    withStatement { stmt ⇒
      val rs = stmt.executeQuery(selectTablesQuery)
      val xs = loop(rs, List.empty[String])
      xs
    }
  }

  def clearTables(): Unit = {
    def createTruncateTableStatement(tableName: String): String = s"TRUNCATE $tableName CASCADE"
    def clearAllTablesQuery: List[String] = tables map createTruncateTableStatement
    withStatement(clearAllTablesQuery foreach _.execute)
  }

  def countTable(table: String): Option[Int] = withStatement { stmt ⇒
    def loop(rs: ResultSet): Option[Int] = if (rs.next) Option(rs.getInt(1)) else None
    loop(stmt.executeQuery(s"SELECT COUNT(*) FROM $table"))
  }

  def tableColumnCount(table: String): Option[Int] = withStatement { stmt ⇒
    val selectNumColumnsQuery: String = s"SELECT COUNT(*) from INFORMATION_SCHEMA.COLUMNS WHERE table_name = '$table'"
    def loop(rs: ResultSet): Option[Int] = if (rs.next) Option(rs.getInt(1)) else None
    loop(stmt.executeQuery(selectNumColumnsQuery))
  }

  def getTable(table: String): List[List[String]] = {
    def getTable(numFields: Int): List[List[String]] = withStatement { stmt ⇒
      def loopFields(rs: ResultSet, fieldNum: Int, acc: List[String]): List[String] =
        if (fieldNum == numFields + 1) acc else loopFields(rs, fieldNum + 1, acc :+ rs.getString(fieldNum))
      def loop(rs: ResultSet, acc: List[List[String]]): List[List[String]] =
        if (rs.next) loop(rs, acc :+ loopFields(rs, 1, Nil)) else acc
      loop(stmt.executeQuery(s"SELECT * FROM $table"), Nil)
    }
    getTable(tableColumnCount(table).get)
  }
}
