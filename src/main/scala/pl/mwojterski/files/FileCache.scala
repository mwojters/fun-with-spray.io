package pl.mwojterski.files

import java.io.{BufferedReader, Reader}
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import pl.mwojterski.files.FileCache.LineCache

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.Source
import scala.ref.SoftReference

private class FileCache(private val path: Path) {

  private val cachedLines: Seq[LineCache] = {
    val cacheBuilder = Seq.newBuilder[LineCache]
    FileCache.foreachLineWithOffset(path) {
      case (offset, line) => cacheBuilder += new LineCache(offset, line)
    }
    cacheBuilder.result()
  }

  def getLine(lineNum: Int)(implicit ec: ExecutionContext): Future[String] = {
    //todo: line negative or too big

    val lineCache = cachedLines(lineNum)
    val cachedLine = lineCache.cachedLine

    // acquire promise from cache or create new if cache was evicted
    var promisedLine: Promise[String] = null
    do {
      val softLine = cachedLine.get
      softLine.get match {
        case Some(promise) => promisedLine = promise

        case None =>
          val promise = Promise[String]()
          if (cachedLine compareAndSet(softLine, new SoftReference(promise)))
            promisedLine = promise completeWith readLine(lineNum, lineCache.charOffset)
      }

    } while (promisedLine == null)

    promisedLine.future
  }

  private def readLine(lineNum: Int, charOffset: Long)(implicit ec: ExecutionContext) =
    Future {
      // body is invoked in the future!
      FileCache.doWithBufferedReader(path) { reader =>
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

  def foreachLineWithOffset[U](path: Path)(fun: ((Long, String)) => U) =
    doWithBufferedReader(path) { reader =>
      val countingIterator = new CountingIterator(reader)
      val lines = new Source {
        override val iter = countingIterator
      }.getLines()

      Iterator.continually(countingIterator.count).zip(lines).foreach(fun)
    }

  def doWithBufferedReader[U](path: Path)(fun: BufferedReader => U) = {
    val reader = Files newBufferedReader(path, Charset.defaultCharset)
    try fun(reader)
    finally reader.close()
  }

  private class CountingIterator(reader: Reader) extends BufferedIterator[Char] {
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

object LineReadTest extends App {
  measure("no-pos") {
    Source.fromFile("C:/s1.log").getLines().toStream.last
  }

  measure("char-pos") {
    FileCache.doWithBufferedReader(Paths.get("C:/s1.log")) { reader =>
      reader skip 29781839
      reader readLine()
    }
  }

  measure("byte-pos") {
    val channel = Files.newByteChannel(Paths.get("C:/s1.log"))
    channel position 0x1C6BB28
    val reader = new BufferedReader(Channels.newReader(channel, Charset.defaultCharset.displayName))
    reader readLine()
  }

  def measure(name: String)(fun: => Any): Unit = {
    val start = System.nanoTime
    val result = fun
    val elapsed = System.nanoTime - start
    val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
    println(s"Function '$name' computed '$result' in: $elapsed nanos = $millis millis")
  }
}
