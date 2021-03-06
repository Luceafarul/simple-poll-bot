package bot.poll.simple

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, LiftIO, Timer}
import cats.implicits._
import cats.effect.implicits._

import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.latestbit.slack.morphism.client.SlackApiClient
import sttp.client.http4s.Http4sBackend

import scala.concurrent.ExecutionContext.global

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.routes._

object Http4sServer {

  def stream[F[_]: ConcurrentEffect](
    config: AppConfig
  )(implicit T: Timer[F], C: ContextShift[F]): Stream[F, Nothing] = {
    for {
      httpClient <- BlazeClientBuilder[F](global).stream
      blocker    <- Stream.resource(Blocker[F])
      slackApiClient <- Stream.resource(
        SlackApiClient
          .build[F](
            Http4sBackend.usingClient(httpClient, blocker)
          )
          .resource()
      )
      tokensDb <- Stream.resource(SlackTokensDb.open[F](config))

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp = (
        new SlackOAuthRoutes[F](slackApiClient, tokensDb, config).routes() <+>
          new SlackPushEventsRoutes[F](slackApiClient, tokensDb, config).routes() <+>
          new SlackInteractionEventsRoutes[F](slackApiClient, tokensDb, config).routes() <+>
          new SlackCommandEventsRoutes[F](slackApiClient, tokensDb, config).routes()
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(config.httpServerPort, config.httpServerHost)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
