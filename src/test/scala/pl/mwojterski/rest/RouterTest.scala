package pl.mwojterski.rest

import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope
import pl.mwojterski.files.FileRepository
import pl.mwojterski.groups.GroupDistributor
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

import scala.concurrent.{ExecutionContext, Future}

class RouterTest extends SpecificationWithJUnit with Specs2RouteTest with HttpService with Mockito {
  def actorRefFactory = system

  "Router" should {

    "return group for POST or GET on /route with id param" in new TestRouter {
      val mockedGroup = "someGroup"
      val uri = "/route?id=2t03md9"

      groupDistributor groupFor anyString returns mockedGroup

      val expectedResponse = check {
        responseAs[String] ==== mockedGroup
      }

      Get(uri) ~> routing ~> expectedResponse
      Post(uri) ~> routing ~> expectedResponse
    }

    "return Json map for GET on /text with params" in new TestRouter {
      implicit val ec = any[ExecutionContext]
      val lines = Map("demo1" -> Left("sampletext"), "demo2" -> Left("sampletext2"))

      fileRepository getFutureLines any[Map[String, String]] returns Future.successful(lines)

      Get("/text?demo1=14&demo2=12") ~> routing ~> check {
        responseAs[String] ==== """{"demo1":"sampletext","demo2":"sampletext2"}"""
      }
    }

  }

  trait TestRouter extends Router with Scope {
    val groupDistributor: GroupDistributor = mock[GroupDistributor]
    val fileRepository: FileRepository = mock[FileRepository]
  }

}


