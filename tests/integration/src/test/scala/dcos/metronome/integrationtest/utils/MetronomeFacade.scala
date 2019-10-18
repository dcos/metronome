package dcos.metronome.integrationtest.utils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.{ Get, Post }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse }
import akka.stream.Materializer
import com.mesosphere.utils.http.RestResult

import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }

class MetronomeFacade(val url: String, implicit val waitTime: FiniteDuration = 30.seconds)(
  implicit
  val system: ActorSystem, mat: Materializer) {

  import scala.concurrent.ExecutionContext.Implicits.global

  //info --------------------------------------------------
  def info(): RestResult[HttpResponse] = {
    result(request(Get(s"$url/info")), waitTime)
  }

  def createJob(jobDef: String): RestResult[HttpResponse] = {
    val e = HttpEntity(ContentTypes.`application/json`, jobDef)
    result(request(Post(s"$url/v1/jobs", e)), waitTime)
  }

  def startRun(jobId: String): RestResult[HttpResponse] = {
    val e = HttpEntity(ContentTypes.`application/json`, "")
    result(request(Post(s"$url/v1/jobs/${jobId}/runs", e)), waitTime)
  }

  def getJob(jobId: String): RestResult[HttpResponse] = {
    result(request(Get(s"$url/v1/jobs")), waitTime)
  }

  def getRuns(jobId: String): RestResult[HttpResponse] = {
    result(request(Get(s"$url/v1/jobs/${jobId}/runs")), waitTime)
  }

  private[this] def request(request: HttpRequest): Future[RestResult[HttpResponse]] = {
    Http(system).singleRequest(request).flatMap { response =>
      response.entity.toStrict(waitTime).map(_.data.decodeString("utf-8")).map(RestResult(response, _))
    }
  }

}
