package pl.mwojterski

import akka.actor.ActorSystem
import spray.routing.SimpleRoutingApp

object ShowUp extends App with SimpleRoutingApp {
  private implicit val system = ActorSystem("simple-system")

  private val route = path("route") {
    parameters('id) { id =>
      complete {
        s"Passed id='$id'"
      }
    }
  }

  private val text = path("text") {
    // implicits for response marshalling
    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import spray.json.DefaultJsonProtocol.{mapFormat, StringJsonFormat}

    implicit val printer = spray.json.CompactPrinter // by default marshaller uses PrettyPrinter

    get {
      parameterMap { files =>
        complete {
          files.transform {
            (file, line) => s"Text for line $line of file '$file'"
          }
        }
      }
    }
  }

  startServer(interface = "localhost", port = 8080) {
     route ~ text
  }
}
