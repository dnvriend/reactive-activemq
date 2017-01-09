import sbt._
import sbt.Keys._
import bintray.BintrayKeys.{bintrayPackageLabels, bintrayPackageAttributes}
import bintray.BintrayPlugin

object BintraySettings extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = BintrayPlugin

  object autoImport {
  }

  import autoImport._

  val defaultSettings: Seq[Setting[_]] = Seq(
    // enable publishing to jcenter
    homepage := Some(url("https://github.com/dnvriend/reactive-activemq")),

    pomIncludeRepository := (_ => false),

    pomExtra := <scm>
      <url>https://github.com/dnvriend/reactive-activemq</url>
      <connection>scm:git@github.com:dnvriend/reactive-activemq.git</connection>
    </scm>
      <developers>
        <developer>
          <id>dnvriend</id>
          <name>Dennis Vriend</name>
          <url>https://github.com/dnvriend</url>
        </developer>
      </developers>,

    publishMavenStyle := true,

    bintrayPackageLabels := Seq("akka-stream", "activemq", "akka"),

    bintrayPackageAttributes ~= (_ ++ Map(
      "website_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/reactive-activemq")),
      "github_repo" -> Seq(bintry.Attr.String("https://github.com/dnvriend/reactive-activemq.git")),
      "issue_tracker_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/reactive-activemq/issues/"))
     )
    )
  )

  override def projectSettings: Seq[Setting[_]] =
    defaultSettings
}