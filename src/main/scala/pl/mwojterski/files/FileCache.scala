package pl.mwojterski.files

import java.io.BufferedReader
import java.lang.ref.SoftReference
import java.nio.channels.{Channels, SeekableByteChannel}
import java.nio.charset.{CoderResult, Charset}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.{ByteBuffer, CharBuffer}
import java.util.concurrent.atomic.AtomicReference

import com.google.common.base.Stopwatch
import com.typesafe.scalalogging.StrictLogging
import pl.mwojterski.files.FileCache.{LineCache, sharedLogger}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.{ExecutionContext, Future, Promise}

private class FileCache(path: Path) {
  require(Files.isReadable(path), s"File '$path' cannot be read!")

  private val cachedLines: IndexedSeq[LineCache] = {
    val cacheBuilder = IndexedSeq.newBuilder[LineCache]
    FileCache.logTime(s"Building cache for '$path'") {
      FileCache.foreachLineWithByteOffset(path) {
        (byteOffset, line) => cacheBuilder += new LineCache(byteOffset, line)
      }
    }
    cacheBuilder.result()
  }

  def linesCount = cachedLines.size

  sharedLogger info s"Created cache for '$path' with $linesCount lines"


  def getLine(lineNum: Int)(implicit ec: ExecutionContext): Future[String] = {
    require(1 <= lineNum && lineNum <= linesCount, s"Invalid lineNum=$lineNum, expected [1..$linesCount]")
    sharedLogger trace s"Getting line $lineNum for '$path'"

    val lineCache = cachedLines(lineNum - 1)
    val cachedLine = lineCache.cachedLine

    // acquire promise from cache or create new if cache was evicted
    var promisedLine: Option[Promise[String]] = None
    do {
      val softLine = cachedLine.get
      promisedLine = Option(softLine.get)

      if (promisedLine.isEmpty) {
        val promise = Promise[String]()
        if (cachedLine compareAndSet(softLine, new SoftReference(promise)))
          promisedLine = Some(promise completeWith readLine(lineNum, lineCache.byteOffset))
      }

    } while (promisedLine.isEmpty)

    promisedLine.get.future
  }

  private def readLine(lineNum: Int, byteOffset: Long)(implicit ec: ExecutionContext) =
    Future {
      // body is invoked in the future!
      FileCache.logTime(s"Reading evicted line $lineNum from '$path'") {
        FileCache.withReadableChannel(path) { channel =>
          channel position byteOffset
          val reader = Channels newReader(channel, Charset.defaultCharset.newDecoder, -1)
          new BufferedReader(reader) readLine()
        }
      }
    }
}

private object FileCache extends StrictLogging {
  def sharedLogger = logger

  private class LineCache(val byteOffset: Long, line: String) {
    // Atomic to ensure only one worker refreshing cache
    // Soft to enable eviction when memory is needed
    // Promise to separate computation from creation
    val cachedLine = new AtomicReference(new SoftReference(Promise.successful(line)))
  }

  private def logTime[R](operation: => String)(op: => R): R = {
    val stopwatch = Stopwatch.createStarted()
    val result = op
    stopwatch.stop()

    logger info s"$operation completed in $stopwatch"
    result
  }

  private def withReadableChannel[U](path: Path)(process: SeekableByteChannel => U) = {
    val channel = Files.newByteChannel(path, StandardOpenOption.READ)
    try process(channel)
    finally channel.close()
  }

  //todo: rewrite this ugly, imperative implementation
  private def foreachLineWithByteOffset[U](path: Path)(fun: (Long, String) => U) =
    FileCache.withReadableChannel(path) { channel =>
      val byteBuffer = ByteBuffer.allocateDirect(8192)
      val charBuffer = CharBuffer.allocate(1)

      var byteOffset = 0L
      val decoder = Charset.defaultCharset.newDecoder

      val sb = new StringBuilder

      var currentOffset = 0L
      var nextOffset = 0L

      var gotCr = false
      var eof = false

      do {
        eof = channel.read(byteBuffer) == -1
        byteBuffer.flip()

        var coderResult: CoderResult = null
        do {
          charBuffer.clear()

          coderResult = decoder.decode(byteBuffer, charBuffer, eof)
          charBuffer.flip()

          if (charBuffer.hasRemaining) {
            val ch = charBuffer.get()

            val ln = ch == '\n'
            val cr = ch == '\r'

            if (ln || cr)
              nextOffset = byteOffset + byteBuffer.position

            if (ln || gotCr) {
              fun(currentOffset, sb.result())

              currentOffset = nextOffset
              sb.clear()
              gotCr = false
            }

            if (cr)
              gotCr = true

            if (!cr && !ln)
              sb += ch
          }
        } while(coderResult.isOverflow)

        byteOffset += byteBuffer.position
        byteBuffer.compact()

      } while (!eof)

      if (sb.nonEmpty)
        fun(currentOffset, sb.result())
    }
}
