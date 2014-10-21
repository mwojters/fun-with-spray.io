package pl.mwojterski.files

import java.nio.charset.Charset
import java.nio.file.Paths

import com.google.common.io.Files
import com.google.common.util.concurrent.MoreExecutors
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class FileCacheTest extends SpecificationWithJUnit {

  val testFilePath = Paths.get((getClass getResource "/testFile.txt" ensuring (_ != null)).toURI)
  val expectedLines = Files.readLines(testFilePath.toFile, Charset.defaultCharset).asScala

  "FileCache" should {

    "properly read file" in new FileCache(testFilePath) with Fixture {
      actualLines === expectedLines
    }

    "save correct byte offsets" in new FileCache(testFilePath) with Fixture {
      evict() // evicting will cause cache to read lines again using saved offsets

      actualLines === expectedLines
    }
  }

  private trait Fixture extends Scope {
    this: FileCache =>

    lazy val actualLines = {
      implicit val ec = ExecutionContext fromExecutor MoreExecutors.directExecutor

      for (lineNo <- 1 to linesCount)
      yield Await result(getLine(lineNo), Duration.Inf)
    }
  }
}
