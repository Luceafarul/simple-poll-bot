package bot.poll.simple.routes

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client.reqresp.views._
import org.latestbit.slack.morphism.client._
import org.latestbit.slack.morphism.codecs.CirceCodecs
import org.latestbit.slack.morphism.events._
import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.templates._
import org.latestbit.slack.morphism.common.SlackTriggerId

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
          case blockActionEvent: SlackInteractionBlockActionEvent => {
            logger.info(s"Received a block action event: ${blockActionEvent}")
            showDummyModal(blockActionEvent.trigger_id)
          }
          case messageActionEvent: SlackInteractionMessageActionEvent => {
            logger.info(s"Received a message action event: ${messageActionEvent}")
            showDummyModal(messageActionEvent.trigger_id)
          }
          case actionSubmissionEvent: SlackInteractionViewSubmissionEvent => {
            actionSubmissionEvent.view.stateParams.state.foreach { state =>
              logger.info(s"Received action submission state: ${state}")
            }
            Ok("") // "" is required here by Slack
          }
          case interactionEvent: SlackInteractionEvent => {
            logger.warn(s"We don't handle this interaction in this example: ${interactionEvent}")
            Ok()
          }
        }
      }
    }

    def showDummyModal(triggerId: SlackTriggerId)(implicit slackApiToken: SlackApiToken) = {
      val modalTemplateExample = new SlackModalTemplateExample()
      slackApiClient.views
        .open(
          SlackApiViewsOpenRequest(
            trigger_id = triggerId,
            view = modalTemplateExample.toModalView()
          )
        )
        .flatMap {
          case Right(resp) => {
            logger.info(s"Modal view has been opened: ${resp}")
            Ok()
          }
          case Left(err) => {
            logger.error(s"Unable to open modal view", err)
            InternalServerError()
          }
        }
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
