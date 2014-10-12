package pl.mwojterski

import akka.actor.ActorSystem
import com.google.common.util.concurrent.MoreExecutors
import pl.mwojterski.conf.Settings
import pl.mwojterski.files.FileRepository
import pl.mwojterski.files.FileRepository.Errors
import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
import spray.json.DefaultJsonProtocol.{StringJsonFormat, mapFormat}
import spray.routing.SimpleRoutingApp

import scala.concurrent.ExecutionContext

object ShowUp extends App with SimpleRoutingApp {
  // implicit actor system for startServer
  private implicit val system = ActorSystem("simple-system")

  // by default Json marshaller uses PrettyPrinter
  private implicit val printer = spray.json.CompactPrinter

  // implicit executor for futures - direct executor (caller runs) for simplicity, can be changed should the need arise
  private implicit val executor = ExecutionContext.fromExecutor(MoreExecutors.directExecutor)

  private val groupDistributor = Settings().groups
  private val fileRepository = FileRepository()

  private val route = path("route") {
    parameters('id) { id =>
      complete(groupDistributor.groupFor(id))
    }
  }

  private val text = path("text") {
    get {
      parameterMap { linesInfo =>
        onSuccess(fileRepository getFutureLines linesInfo) { lines =>
          complete {
            lines.mapValues {
              case Left(line) => line
              case Right(error) => error match {
                case Errors.NoSuchLine => "<invalid line>"
                case Errors.UnknownFile => "<unknown file>"
              }
            }
          }
        }
      }
    }
  }

  startServer(interface = "localhost", port = 8080) {
    route ~ text
  }
}
