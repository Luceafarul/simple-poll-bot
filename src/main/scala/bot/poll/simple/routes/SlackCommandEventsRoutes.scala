package bot.poll.simple.routes

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.templates.PollModal
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client.reqresp.views.SlackApiViewsOpenRequest
import org.latestbit.slack.morphism.client.{SlackApiClientT, SlackApiToken}
import org.latestbit.slack.morphism.common.{SlackTeamId, SlackTriggerId}

class SlackCommandEventsRoutes[F[_]: Sync](
  slackApiClient: SlackApiClientT[F],
  implicit val tokensDb: SlackTokensDb[F],
  implicit val config: AppConfig
) extends StrictLogging
    with SlackEventsMiddleware {

  def routes(): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case req @ POST -> Root / "command" => {
        slackSignedRoutes[F](req) {
          req.decode[UrlForm] { form =>
            (form.getFirst("text"),
             form.getFirst("response_url"),
             form.getFirst("channel_id"),
             form.getFirst("team_id"),
             form.getFirst("trigger_id")) match {
              case (Some(text), Some(responseUrl), Some(channelId), Some(teamId), Some(trigger_id)) => {

                extractSlackWorkspaceToken[F](SlackTeamId(teamId)) { implicit slackApiToken =>
//                  slackApiClient.chat
//                    .postMessage(
//                      SlackApiChatPostMessageRequest(
//                        channel = SlackChannelId(channelId),
//                        text = text,
//                        blocks = Some(pollModal.renderBlocks())
//                      )
//                    )
//                slackApiClient.events
//                  .reply(
//                    response_url = responseUrl,
//                    SlackApiEventMessageReply(
//                      text = commandReply.renderPlainText(),
//                      blocks = commandReply.renderBlocks(),
//                      response_type = Some(SlackResponseTypes.Ephemeral)
//                    )
//                  )
//                    .flatMap { resp =>
//                      resp.leftMap(err => logger.error(err.getMessage))
//                      Ok()
//                    }

                  showBasicPollModal(SlackTriggerId(trigger_id), text)
                }
              }
              case _ => BadRequest()
            }
          }
        }
      }
    }
  }

  def showBasicPollModal(triggerId: SlackTriggerId, initialValue: String = "")(implicit slackApiToken: SlackApiToken) = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    val modalTemplateExample = new PollModal(initialValue)
    slackApiClient.views
      .open(
        SlackApiViewsOpenRequest(
          trigger_id = triggerId,
          view = modalTemplateExample.toModalView()
        )
      )
      .flatMap {
        case Right(resp) =>
          logger.info(s"Modal view has been opened: $resp")
          Ok()
        case Left(err) =>
          logger.error(s"Unable to open modal view", err)
          InternalServerError()
      }
  }

}
