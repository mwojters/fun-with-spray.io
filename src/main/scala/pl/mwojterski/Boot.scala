package pl.mwojterski

import akka.actor.ActorSystem
import com.google.common.util.concurrent.MoreExecutors
import pl.mwojterski.conf.Settings
import pl.mwojterski.files.FileRepository
import pl.mwojterski.files.FileRepository.{Fails, Result}
import pl.mwojterski.groups.GroupDistributor
import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
import spray.json.DefaultJsonProtocol.{StringJsonFormat, mapFormat}
import spray.routing.SimpleRoutingApp

import scala.concurrent.ExecutionContext

object Boot extends App with SimpleRoutingApp {

  // for startServer
  private implicit val system = ActorSystem("simple-system")

  // by default Json marshaller uses PrettyPrinter
  private implicit val printer = spray.json.CompactPrinter

  // for fileRepository, direct executor (caller runs) for simplicity, can be changed should the need arise
  private implicit val executor = ExecutionContext.fromExecutor(MoreExecutors.directExecutor)

  private val settings = Settings()
  private val groupDistributor = GroupDistributor(settings.groups)
  private val fileRepository = FileRepository(settings.files)

  private val route = path("route") {
    parameters('id) { id =>
      complete(groupDistributor.groupFor(id))
    }
  }

  private val text = path("text") {
    get {
      parameterMap { requestedLines =>
        onSuccess(fileRepository getFutureLines requestedLines) { resultLines =>
          complete(resultLines mapValues resultTranslator)
        }
      }
    }
  }

  protected def resultTranslator(result: Result): String = result match {
    case Left(line) => line
    case Right(error) => error match {
      case Fails.UnknownFile => "<unknown file>"
      case Fails.InvalidLine => "<invalid line>"
      case Fails.NoSuchLine => "<no such line>"
    }
  }

  startServer(interface = "localhost", port = settings.port) {
    route ~ text
  }
}
