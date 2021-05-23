package bot.poll.simple.routes

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.templates._
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client._
import org.latestbit.slack.morphism.client.reqresp.chat.SlackApiChatPostMessageRequest
import org.latestbit.slack.morphism.client.reqresp.views._
import org.latestbit.slack.morphism.codecs.CirceCodecs
import org.latestbit.slack.morphism.common.{SlackChannelId, SlackTriggerId}
import org.latestbit.slack.morphism.events._

class SlackInteractionEventsRoutes[F[_]: Sync](
  slackApiClient: SlackApiClientT[F],
  implicit val tokensDb: SlackTokensDb[F],
  implicit val config: AppConfig
) extends StrictLogging
    with SlackEventsMiddleware
    with CirceCodecs {

  def routes(): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    def onEvent(event: SlackInteractionEvent): F[Response[F]] = {
      extractSlackWorkspaceToken[F](event.team.id) { implicit apiToken =>
        event match {
          case actionSubmissionEvent: SlackInteractionViewSubmissionEvent => {
            logger.info(s"Received action submission state: $actionSubmissionEvent")
            val pollModal = new PollModal()
            slackApiClient.chat
              .postMessage(
                SlackApiChatPostMessageRequest(
                  channel = SlackChannelId("C021Y0R1MNH"),
                  text = "hello",
                  blocks = Some(pollModal.renderBlocks())
                )
              )
              .flatMap { resp =>
                resp.leftMap(err => logger.error(err.getMessage))
                Ok()
              }
            Ok("") // "" is required here by Slack
          }
          case shortcutEvent: SlackInteractionShortcutEvent => {
            logger.info(s"Received shortcut interaction event: $shortcutEvent")
            showBasicPollModal(shortcutEvent.trigger_id).flatMap {
              case Right(resp) =>
                logger.info(s"Modal view has been opened: $resp")
                Ok()
              case Left(err) =>
                logger.error(s"Unable to open modal view", err)
                InternalServerError()
            }
          }
          case interactionEvent: SlackInteractionEvent => {
            logger.warn(s"We don't handle this interaction in this example: $interactionEvent")
            Ok()
          }
        }
      }
    }

    def showBasicPollModal(triggerId: SlackTriggerId)(implicit slackApiToken: SlackApiToken) = {
      val modalTemplateExample = new PollModal()
      slackApiClient.views
        .open(
          SlackApiViewsOpenRequest(
            trigger_id = triggerId,
            view = modalTemplateExample.toModalView()
          )
        )
    }

    HttpRoutes.of[F] {
      case req @ POST -> Root / "interaction" => {
        slackSignedRoutes[F](req) {
          req.decode[UrlForm] { form =>
            OptionT
              .fromOption[F](
                form.getFirst("payload")
              )
              .flatMap(decodeJson[F, SlackInteractionEvent])
              .map { event =>
                onEvent(event)
              }
              .value
              .flatMap(_.getOrElse(BadRequest()))
          }

        }
      }
    }

  }

}
