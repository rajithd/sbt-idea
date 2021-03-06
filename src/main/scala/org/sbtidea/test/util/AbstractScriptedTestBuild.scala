package org.sbtidea.test.util

import sbt._
import org.apache.commons.io.FileUtils.listFiles
import org.apache.commons.io.FilenameUtils.removeExtension
import scala.xml.Utility.trim
import xml.{PrettyPrinter, XML, Node}
import collection.JavaConverters._
import xml.transform.{RewriteRule, RuleTransformer}

abstract class AbstractScriptedTestBuild extends Build {
  lazy val assertExpectedXmlFiles = TaskKey[Unit]("assert-expected-xml-files")

	lazy val scriptedTestSettings = Seq(assertExpectedXmlFiles := assertXmlsTask)

  private def assertXmlsTask {
    val expectedFiles = listFiles(file("."), Array("expected"), true).asScala
    expectedFiles.map(assertExpectedXml).foldLeft[Option[String]](None) {
      (acc, fileResult) => if (acc.isDefined) acc else fileResult
    } foreach sys.error
  }

  private def assertExpectedXml(expectedFile: File):Option[String] = {
    val actualFile = new File(removeExtension(expectedFile.getAbsolutePath))
    if (actualFile.exists) assertExpectedXml(expectedFile, actualFile)
    else Some("Expected file " + actualFile.getAbsolutePath + " does not exist.")
  }

  private def assertExpectedXml(expectedFile: File, actualFile: File): Option[String] = {

    /* Strip the suffix that is randomly generated from content url so that comparisons can work */
    def processActual(node: xml.Node): xml.Node = {
      if (!actualFile.getName.contains(".iml")) node
      else {
        
        def elementMatches(e: xml.Node): Boolean = {
          val url = (e \ "@url").text
          url.startsWith("file:///tmp/sbt_") && url.endsWith("/simple-project")
        }

        new RuleTransformer(new RewriteRule {
          override def transform (n: Node): Seq[Node] = n match {
            case e: xml.Elem if e.label == "content" && elementMatches(e) =>
              <content url="file:///tmp/sbt_/simple-project">{e.child}</content>
            case _ => n
          }
        }).transform(node).head

      }
    }

    val actualXml = processActual(trim(XML.loadFile(actualFile)))
    val expectedXml = trim(XML.loadFile(expectedFile))
    if (!actualXml.equals(expectedXml)) Some(formatErrorMessage(actualFile, actualXml, expectedXml)) else None
  }

  private def formatErrorMessage(actualFile: File, actualXml: Node, expectedXml: Node): String = {
    val pp = new PrettyPrinter(1000, 2)
    val msg = new StringBuilder
    msg.append("Xml file " + actualFile.getName + " does not equal expected:")
    msg.append("\n********** Expected **********\n ")
    pp.format(expectedXml, msg)
    msg.append("\n*********** Actual ***********\n ")
    pp.format(actualXml, msg)
    msg.toString
  }
}
