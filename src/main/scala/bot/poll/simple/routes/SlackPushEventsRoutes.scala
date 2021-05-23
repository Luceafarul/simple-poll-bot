package bot.poll.simple.routes

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client.SlackApiClientT
import org.latestbit.slack.morphism.codecs.CirceCodecs
import org.latestbit.slack.morphism.common.SlackTeamId
import org.latestbit.slack.morphism.events._

class SlackPushEventsRoutes[F[_]: Sync](
  slackApiClient: SlackApiClientT[F],
  implicit val tokensDb: SlackTokensDb[F],
  implicit val config: AppConfig
) extends StrictLogging
    with SlackEventsMiddleware
    with CirceCodecs {

  def routes(): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    def onPushEvent(event: SlackPushEvent) = event match {
      case urlVerEv: SlackUrlVerificationEvent => {
        logger.info(s"Received a challenge request:\n${urlVerEv.challenge}")
        Ok(urlVerEv.challenge)
      }
      case callbackEvent: SlackEventCallback => {
        extractSlackWorkspaceToken[F](callbackEvent.team_id) { implicit slackApiToken =>
          callbackEvent.event match {

            case body: SlackAppHomeOpenedEvent => {
              logger.info(s"User opened home: $body")
              Ok()
            }

            case msg: SlackUserMessage => {
              logger.info(
                s"Received a user message '${msg.text.getOrElse("-")}' in ${msg.channel.getOrElse("-")}"
              )
              Ok()
            }

            case removeToken: SlackTokensRevokedEvent => {
              removeTokens(callbackEvent.team_id, removeToken)
            }

            case botMessage: SlackBotMessage => {
              logger.info(
                s"Received a bot message '${botMessage.text.getOrElse("-")}' in ${botMessage.channel.getOrElse("-")}"
              )
              Ok()
            }

            case unknownBody: SlackEventCallbackBody => {
              logger.warn(s"We don't handle this callback event we received in this example: ${unknownBody}")
              Ok()
            }
          }
        }
      }

      case pushEvent: SlackPushEvent => {
        logger.warn(s"We don't handle this push event we received in this example: ${pushEvent}")
        Ok()
      }

    }

    def removeTokens(teamId: SlackTeamId, re: SlackTokensRevokedEvent): F[Response[F]] = {
      tokensDb.removeTokens(teamId, re.tokens.oauth.toSet ++ re.tokens.bot.toSet).flatMap(_ => Ok())
    }

    HttpRoutes.of[F] {
      case req @ POST -> Root / "push" => {
        slackSignedRoutes[F, SlackPushEvent](req)(onPushEvent)
      }
    }
  }

}
