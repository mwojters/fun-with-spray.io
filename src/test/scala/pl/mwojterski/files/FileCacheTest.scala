package pl.mwojterski.files

import java.nio.file.Paths

import com.google.common.util.concurrent.MoreExecutors
import org.specs2.mutable.Specification

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.io.{Codec, Source}

class FileCacheTest extends Specification {

  "FileCache" should {

    "- properly read file" in {

      // given
      val testFile = getClass getResource "/testFile.txt" ensuring (_ != null)

      // when
      val cache = new FileCache(Paths.get(testFile.toURI))

      // then
      implicit val ec = ExecutionContext fromExecutor MoreExecutors.directExecutor

      val actual =
        for (lineNo <- 1 to cache.linesCount)
        yield Await result(cache getLine lineNo, Duration.Inf)

      val expected = Source.fromURL(testFile)(Codec.default).getLines().toIndexedSeq
      actual must containTheSameElementsAs(expected)
    }
  }
}
