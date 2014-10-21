package pl.mwojterski.files

import java.net.URL
import java.nio.file.Paths

import com.google.common.util.concurrent.MoreExecutors
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.io.{Codec, Source}

class FileCacheTest extends Specification {

  val testFile = getClass getResource "/testFile.txt" ensuring (_ != null)
  val expectedLines = readExpectedLines(testFile)
  val testFilePath = Paths.get(testFile.toURI)

  "FileCache" should {

    "- properly read file" in new FileCache(testFilePath) with Fixture {
      actualLines === expectedLines
    }

    "- save correct byte offsets" in new FileCache(testFilePath) with Fixture {
      evict() // evicting will cause cache to read lines again using saved offsets

      actualLines === expectedLines
    }
  }

  private trait Fixture extends Scope {
    this: FileCache =>

    val actualLines = {
      implicit val ec = ExecutionContext fromExecutor MoreExecutors.directExecutor

      for (lineNo <- 1 to linesCount)
      yield Await result(getLine(lineNo), Duration.Inf)
    }
  }

  private def readExpectedLines(testFile: URL): IndexedSeq[String] = {
    val source = Source.fromURL(testFile)(Codec.default)
    try source.getLines().toIndexedSeq
    finally source.close()
  }
}
