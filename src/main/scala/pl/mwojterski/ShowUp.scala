package pl.mwojterski

import akka.actor._
import spray.routing.SimpleRoutingApp

object ShowUp extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("simple-system")

  startServer(interface = "localhost", port = 8080) {
    path("hello") {
      parameters('id) { id =>
        complete {
          s"Passed id='$id'ss"
        }
      }
    }
  }
}
