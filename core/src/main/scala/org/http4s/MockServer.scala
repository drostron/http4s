package org.http4s

import scala.language.reflectiveCalls

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Enumeratee, Iteratee}

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: Request): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) {
        handler =>
          req.body.run(handler) flatMap {
            responder => render(responder)
          } recover {
            case t => onError(t)
          }
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def render(responder: Responder): Future[Response] = {
    val it: Iteratee[Array[Byte], Array[Byte]] = Iteratee.consume()
    val etee = Enumeratee.map[Chunk] { chunk => chunk.bytes }
    val bytes = responder.body.run(etee.transform(it))
    bytes map { body =>
      Response(statusLine = responder.statusLine, headers = responder.headers, body = body)
    }

  }

  def onNotFound: MockServer.Response = Response(statusLine = StatusLine.NotFound)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception => Response(statusLine = StatusLine.InternalServerError)
  }
}

object MockServer {
  case class Response(
    statusLine: StatusLine = StatusLine.Ok,
    headers: Headers = Headers.Empty,
    body: Array[Byte] = Array.empty
  )
}