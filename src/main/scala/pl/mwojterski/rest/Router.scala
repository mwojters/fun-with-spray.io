package pl.mwojterski.rest

import pl.mwojterski.files.FileRepository
import pl.mwojterski.files.FileRepository._
import pl.mwojterski.groups.GroupDistributor
import pl.mwojterski.rest.Server._

import scala.concurrent.ExecutionContext

trait Router {

  protected def groupDistributor: GroupDistributor
  protected def fileRepository: FileRepository

  // expose as protected to allow overriding with different executors
  protected def fileRepoExecutor: ExecutionContext = ExecutionContext.global

  private val route = path("route") {
    parameters('id) { id =>
      complete(groupDistributor.groupFor(id))
    }
  }

  private val text = {
    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import spray.json.DefaultJsonProtocol.{StringJsonFormat, mapFormat}

    implicit val executor = fileRepoExecutor
    implicit val printer = spray.json.CompactPrinter // by default Json marshaller uses PrettyPrinter

    path("text") {
      get {
        parameterMap { requestedLines =>
          onSuccess(fileRepository getFutureLines requestedLines) { resultLines =>
            complete(resultLines mapValues resultTranslator)
          }
        }
      }
    }
  }

  val routing = route ~ text

  protected def resultTranslator(result: Result): String = result match {
    case Left(line) => line
    case Right(error) => error match {
      case Fails.UnknownFile => "<unknown file>"
      case Fails.InvalidLine => "<invalid line>"
      case Fails.NoSuchLine => "<no such line>"
    }
  }
}
