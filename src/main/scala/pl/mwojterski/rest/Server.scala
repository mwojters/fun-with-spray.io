package pl.mwojterski.rest

import akka.actor.ActorSystem
import pl.mwojterski.conf.Settings
import pl.mwojterski.files.FileRepository
import pl.mwojterski.groups.GroupDistributor
import spray.routing.SimpleRoutingApp

import scala.concurrent.duration.DurationInt

object Server extends App with SimpleRoutingApp with Router {

  private val settings = Settings()

  val groupDistributor = GroupDistributor(settings.groups)
  val fileRepository = FileRepository(settings.files)

  // for startServer
  implicit val system = ActorSystem("simple-system")
  implicit val bindTimeout = 5.minutes // need some time for cache building

  startServer(interface = "localhost", port = settings.port)(routing)
}
