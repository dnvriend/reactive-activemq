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

name := "reactive-activemq"

organization := "com.github.dnvriend"

version := "0.0.28"

scalaVersion := "2.12.3"

crossScalaVersions := Seq("2.11.11", "2.12.3")

testOptions in Test += Tests.Argument("-oD")

libraryDependencies ++= {
  val akkaVersion = "2.5.4"
  val scalazVersion = "7.2.8"
  val sprayJsonVersion = "1.3.3"
  Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-camel" % akkaVersion,
    "io.spray" %% "spray-json" % sprayJsonVersion,
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.apache.activemq" % "activemq-camel" % "5.14.3",
    "com.google.protobuf" % "protobuf-java" % "3.1.0",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "ch.qos.logback" % "logback-classic" % "1.1.8" % Test,
    "org.scalatest" %% "scalatest" % "3.0.1" % Test
  )
}

fork in Test := true

parallelExecution in Test := false

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

// enable scala code formatting //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform

// Scalariform settings
SbtScalariform.autoImport.scalariformPreferences := SbtScalariform.autoImport.scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)

// enable updating file headers //
import de.heikoseeberger.sbtheader.license.Apache2_0

headers := Map(
  "scala" -> Apache2_0("2016", "Dennis Vriend"),
  "conf" -> Apache2_0("2016", "Dennis Vriend", "#")
)

enablePlugins(AutomateHeaderPlugin)