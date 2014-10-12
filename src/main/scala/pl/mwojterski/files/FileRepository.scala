package pl.mwojterski.files

import java.nio.file.Path

import pl.mwojterski.files.FileRepository.Result

import scala.concurrent.{ExecutionContext, Future}

class FileRepository private(files: Map[String, Path]) {

  def getFutureLine(file: String, line: String): Future[Result] = {
    Future.successful(Left("some line"))
  }

  def getFutureLines(lines: Map[String, String])(implicit ec: ExecutionContext): Future[Map[String, Result]] = {
    val futureEntries = Future.traverse(lines) {
      case (file, lineNo) =>
        val futureLine = getFutureLine(file, lineNo)
        futureLine.map {
          line => (file, line)
        }
    }
    futureEntries.map(_.toMap)
  }
}

object FileRepository {
  type Result = Either[String, Error]
  type Error = Errors.Value

  object Errors extends Enumeration {
    val UnknownFile, NoSuchLine, Soem1, Soem4 = Value
  }

  def apply() = new FileRepository(Map.empty)
}
