package pl.mwojterski.files

import java.io.{BufferedReader, Reader}
import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.StrictLogging
import pl.mwojterski.files.FileCache.LineCache

import scala.collection.immutable.IndexedSeq
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.Source
import scala.ref.SoftReference

private class FileCache(path: Path) extends StrictLogging {
  require(Files.isReadable(path), s"File '$path' cannot be read!")
  logger info s"Created cache for '$path'"

  private val cachedLines: IndexedSeq[LineCache] = {
    val cacheBuilder = IndexedSeq.newBuilder[LineCache]
    FileCache.foreachLineWithOffset(path) {
      case (offset, line) => cacheBuilder += new LineCache(offset, line)
    }
    cacheBuilder.result()
  }

  val linesCount = cachedLines.size

  def getLine(lineNum: Int)(implicit ec: ExecutionContext): Future[String] = {
    require(1 <= lineNum && lineNum <= linesCount, s"Invalid lineNum=$lineNum, expected [1..$linesCount]")
    logger debug s"Getting line $lineNum for '$path'"

    val lineCache = cachedLines(lineNum - 1)
    val cachedLine = lineCache.cachedLine

    // acquire promise from cache or create new if cache was evicted
    var promisedLine: Option[Promise[String]] = None
    do {
      val softLine = cachedLine.get
      promisedLine = softLine.get

      if (promisedLine.isEmpty) {
        val promise = Promise[String]()
        if (cachedLine compareAndSet(softLine, new SoftReference(promise)))
          promisedLine = Some(promise completeWith readLine(lineNum, lineCache.charOffset))
      }

    } while (promisedLine.isEmpty)

    promisedLine.get.future
  }

  private def readLine(lineNum: Int, charOffset: Long)(implicit ec: ExecutionContext) =
    Future {
      logger info s"Reading evicted line $lineNum from '$path'"
      // body is invoked in the future!
      FileCache.withBufferedReader(path) { reader =>
        reader skip charOffset
        reader readLine()
      }
    }
}

private object FileCache {

  private class LineCache(val charOffset: Long, line: String) {
    // Atomic to ensure only one worker refreshing cache
    // Soft to enable eviction when memory is needed
    // Promise to separate computation from creation
    val cachedLine = new AtomicReference(new SoftReference(Promise.successful(line)))
  }

  private def foreachLineWithOffset[U](path: Path)(fun: ((Long, String)) => U) =
    withBufferedReader(path) { reader =>
      val countingIterator = new CountingIterator(reader)
      val lines = new Source {
        override val iter = countingIterator
      }.getLines()

      Iterator.continually(countingIterator.count).zip(lines).foreach(fun)
    }

  private def withBufferedReader[U](path: Path)(fun: BufferedReader => U) = {
    val reader = Files newBufferedReader(path, Charset.defaultCharset)
    try fun(reader)
    finally reader.close()
  }

  private[this] class CountingIterator(reader: Reader) extends BufferedIterator[Char] {
    private var buffer: Int = -1
    private var counter: Long = 0

    def count = counter

    override def head: Char =
      if (hasNext) buffer.toChar
      else Iterator.empty.next()

    override def next(): Char =
      try head
      finally {
        counter += 1
        buffer = -1
      }

    override def hasNext: Boolean = {
      if (buffer < 0)
        buffer = reader.read()

      buffer >= 0
    }
  }

}

//object LineReadTest extends App {
//  import java.nio.file.Paths
//  import java.nio.channels.Channels
//  import java.util.concurrent.TimeUnit
//
//  measure("no-pos") {
//    Source.fromFile("C:/s1.log").getLines().toStream.last
//  }
//
//  measure("char-pos") {
//    val reader = Files.newBufferedReader(Paths.get("C:/s1.log"), Charset.defaultCharset)
//    reader skip 29781839
//    reader readLine()
//  }
//
//  measure("byte-pos") {
//    val channel = Files.newByteChannel(Paths.get("C:/s1.log"))
//    channel position 0x1C6BB28
//    val reader = new BufferedReader(Channels.newReader(channel, Charset.defaultCharset.displayName))
//    reader readLine()
//  }
//
//  def measure(name: String)(fun: => Any): Unit = {
//    val start = System.nanoTime
//    val result = fun
//    val elapsed = System.nanoTime - start
//    val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
//    println(s"Function '$name' computed '$result' in: $elapsed nanos = $millis millis")
//  }
//}
