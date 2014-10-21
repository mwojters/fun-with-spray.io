package pl.mwojterski.files

import java.nio.{CharBuffer, ByteBuffer}
import java.nio.channels.ReadableByteChannel
import java.nio.charset.{CoderResult, Charset}

private[files] class CountingChannelReader(channel: ReadableByteChannel, charset: Charset)
  extends BufferedIterator[Char] {

  //todo: surrogate characters handling!

  private val decoder = charset.newDecoder

  private val byteBuffer = ByteBuffer allocateDirect 8192
  private val charBuffer = CharBuffer allocate 1

  // start with exhausted buffers
  byteBuffer.flip()
  charBuffer.flip()

  private var headBytes = 0
  private var byteCount = 0L

  private var decoderResult = CoderResult.UNDERFLOW
  private var headBuffer = -1
  private var eof = false

  override def head: Char =
    if (hasNext) headBuffer.toChar
    else throw new NoSuchElementException("head on empty buffered iterator")

  override def next(): Char =
    try head
    finally {
      byteCount += headBytes
      headBuffer = -1
    }

  override def hasNext: Boolean = headBuffer >= 0 || fillHeadBuffer()

  def count: Long = byteCount

  private def fillHeadBuffer(): Boolean = {
    if (decoderResult.isUnderflow && !eof)
      readMoreBytes()

    readHeadBuffer()
  }

  private def readMoreBytes(): Unit = {
    byteBuffer.compact()
    eof = channel.read(byteBuffer) == -1
    byteBuffer.flip()
  }

  private def readHeadBuffer(): Boolean = {
    if (!charBuffer.hasRemaining)
      decodeMoreChars()

    val hasMoreChars = charBuffer.hasRemaining
    if (hasMoreChars)
      headBuffer = charBuffer.get()

    hasMoreChars
  }

  private def decodeMoreChars(): Unit = {
    charBuffer.clear()
    val bytePosition = byteBuffer.position
    decoderResult = decoder.decode(byteBuffer, charBuffer, eof)
    headBytes = byteBuffer.position - bytePosition
    charBuffer.flip()
  }
}
