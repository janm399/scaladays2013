package org.eigengo.sd.api

import akka.actor.{Actor, ActorRef}
import spray.http._
import spray.http.HttpResponse
import scala.util.Success
import scala.util.Failure
import org.eigengo.sd.core.Begin
import org.eigengo.sd.core.CoordinatorActor.{SingleImage, FrameChunk}

object StreamingRecogService {
  def makePattern(start: String) = (start + """(.*)""").r

  val RootUri   = "/recog"
  val MJPEGUri  = makePattern("/recog/mjpeg/")
  val H264Uri   = makePattern("/recog/h264/")
  val RtspUri   = makePattern("/recog/rtsp/")
  val StaticUri = makePattern("/recog/static/")
}

/**
 * Given the ``coordinator``, it receives messages that represent the HTTP requests;
 * processes them and routes messages into the core of our system.
 *
 * @param coordinator the coordinator that does all the heavy lifting
 */
class StreamingRecogService(coordinator: ActorRef) extends Actor {
  import akka.pattern.ask
  import scala.concurrent.duration._
  import context.dispatcher
  implicit val timeout = akka.util.Timeout(2.seconds)

  import StreamingRecogService._

  def receive = {
    // begin a transaction
    case HttpRequest(HttpMethods.POST, RootUri, _, _, _) =>
      val client = sender
      (coordinator ? Begin(1)).mapTo[String].onComplete {
        case Success(sessionId) => client ! HttpResponse(entity = sessionId)
        case Failure(ex)        => client ! HttpResponse(entity = ex.getMessage, status = StatusCodes.InternalServerError)
      }

    // stream to /recog/mjpeg/:id
    // stream to /recog/h264/:id
    // POST to /recog/static/:id
    // POST to /recog/rtsp/:id

    // all other requests
    case HttpRequest(method, uri, _, _, _) =>
      println("XXX")
      sender ! HttpResponse(entity = "No such endpoint. That's all we know.", status = StatusCodes.NotFound)
  }

}
