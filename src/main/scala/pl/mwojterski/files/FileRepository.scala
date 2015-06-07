package pl.mwojterski.files

import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.StrictLogging
import pl.mwojterski.files.FileRepository.{Fails, Failure, Result}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class FileRepository private (cachedFiles: Map[String, FileCache]) {

  def getFutureLines(lines: Map[String, String])(implicit ec: ExecutionContext): Future[Map[String, Result]] =
    Future.traverse(lines) {
      case (file, lineNo) =>
        getFutureLine(file, lineNo) map (file -> _)

    }.map(_.toMap)

  private def getFutureLine(file: String, line: String)(implicit ec: ExecutionContext): Future[Result] =
    parseLineNum(line).map { lineNum =>
      cachedFiles.get(file).map { fileCache =>

        if (lineNum <= fileCache.linesCount)
          fileCache.getLine(lineNum) map (Left(_))

        else failWith(Fails.NoSuchLine)

      }.getOrElse(failWith(Fails.UnknownFile))
    }.getOrElse(failWith(Fails.InvalidLine))

  private def failWith(failure: Failure): Future[Result] = Future.successful(Right(failure))

  private def parseLineNum(line: String): Option[Int] =
    try {
      val lineNum = line.toInt
      if (lineNum < 1) None else Some(lineNum)

    } catch {
      case _: NumberFormatException => None
    }
}

object FileRepository extends StrictLogging {
  type Result = Either[String, Failure]
  type Failure = Fails.Value

  object Fails extends Enumeration {
    val UnknownFile, NoSuchLine, InvalidLine = Value
  }

  def apply(files: Map[String, Path]) = {
    logger info s"Creating repository for files: ${files.keys.mkString(", ")}"

    // build caches for unique paths and reuse if different paths point to the same file
    val cachedPaths = mutable.Map[Path, FileCache]()
    files.values.toSet[Path].foreach { path =>
      val optionalAliasPath = cachedPaths.keysIterator.find(Files.isSameFile(_, path))
      val pathCache = optionalAliasPath match {
        case Some(aliasPath) => cachedPaths(aliasPath)
        case None => new FileCache(path)
      }
      cachedPaths += path -> pathCache
    }

    // link file aliases with caches
    val cachedFiles = files.transform {
      (file, path) => cachedPaths(path)
    }
    new FileRepository(cachedFiles)
  }
}