package bot.poll.simple.routes

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.templates._
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.JsonCodec
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client._
import org.latestbit.slack.morphism.client.reqresp.chat.SlackApiChatPostMessageRequest
import org.latestbit.slack.morphism.client.reqresp.views._
import org.latestbit.slack.morphism.codecs.CirceCodecs
import org.latestbit.slack.morphism.common.{SlackChannelId, SlackTriggerId}
import org.latestbit.slack.morphism.events._
import org.latestbit.slack.morphism.views.{SlackHomeView, SlackModalView}

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
            actionSubmissionEvent.view.view match {
              case SlackModalView(title, blocks, close, submit, _, _, _, _, _, _) =>
                val slackChannelId = SlackChannelId(blocks.head.block_id.get.value)
                val pollModal      = new PollModal(slackChannelId.value)

                import io.circe.generic.semiauto._
                import bot.poll.simple.models._
                import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

//                @JsonCodec final case class PollOption(title: PollField)
//                @JsonCodec final case class PollField(`type`: String, value: String)

                val res =
                  actionSubmissionEvent.view.stateParams.state.map { state =>
                    state.values.values.map { opt =>
                      logger.info(s"JSON: $opt")
                      opt.as[PollOption]
                    }
                  }

                logger.info(s"THIS IS RES: $res")

                val maybePollOption = res.map { list =>
                  list.map {
                    case Right(pollOpt) =>
                      logger.info(s"THIS IS POLL OPT: $pollOpt")
                      pollOpt
                  }
                }

                val messageBlocks = maybePollOption.map { pollOptions =>
                  new PollMessage(pollOptions.toList).renderBlocks()
                }

                slackApiClient.chat
                  .postMessage(
                    SlackApiChatPostMessageRequest(
                      channel = slackChannelId,
                      text = title.text,
                      blocks = messageBlocks
                    )
                  )
                  .flatMap { resp =>
                    resp.leftMap(err => logger.error(err.getMessage))
                    Ok()
                  }
              case SlackHomeView(_, _, _, _) => BadRequest()
            }
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
      val modalTemplateExample = new PollModal("")
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
