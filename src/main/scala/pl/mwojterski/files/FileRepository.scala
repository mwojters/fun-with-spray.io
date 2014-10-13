package pl.mwojterski.files

import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.StrictLogging
import pl.mwojterski.files.FileRepository.{Fails, Failure, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable

class FileRepository private (cachedFiles: Map[String, FileCache]) {

  def getFutureLines(lines: Map[String, String])(implicit ec: ExecutionContext): Future[Map[String, Result]] = {
    // transforms map of future results into future map of results
    val futureEntries = Future.traverse(lines) {
      case (file, lineNo) =>
        val futureLine = getFutureLine(file, lineNo)
        futureLine.map {
          line => (file, line)
        }
    }
    futureEntries.map(_.toMap)
  }

  def getFutureLine(file: String, line: String)(implicit ec: ExecutionContext): Future[Result] = {
    parseLineNum(line).map { lineNum =>
      cachedFiles.get(file).map { fileCache =>
        if (lineNum <= fileCache.linesCount)
          fileCache.getLine(lineNum).map(Left(_))

        else failWith(Fails.NoSuchLine)

      } .getOrElse(failWith(Fails.UnknownFile))
    } .getOrElse(failWith(Fails.InvalidLine))
  }

  private def failWith(failure: Failure): Future[Result] = Future.successful(Right(failure))

  private def parseLineNum(line: String): Option[Int] = {
    try {
      val lineNum = line.toInt
      if (lineNum < 1) None else Some(lineNum)
    } catch {
      case _ : NumberFormatException => None
    }
  }
}

object FileRepository extends StrictLogging {
  type Result = Either[String, Failure]
  type Failure = Fails.Value

  object Fails extends Enumeration {
    val UnknownFile, NoSuchLine, InvalidLine = Value
  }

  def apply(files: Map[String, Path]) = {
    val cachedPaths = mutable.Map[Path, FileCache]()

    // build caches for unique paths and reuse if different paths point to the same file
    files.values.toSet[Path].foreach { path =>
      val cache = cachedPaths.keysIterator.find(Files.isSameFile(_, path)) match {
        case Some(aliasPath) => cachedPaths(aliasPath)
        case None => new FileCache(path)
      }
      cachedPaths += path -> cache
    }

    // transform map from name->path to name->cache
    val cachedFiles = files.transform {
      case (file, path) => cachedPaths(path)
    }

    logger info s"Creating repository with files: ${cachedFiles.keys}"
    new FileRepository(cachedFiles)
  }
}
