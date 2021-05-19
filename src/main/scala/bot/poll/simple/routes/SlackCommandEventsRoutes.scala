package bot.poll.simple.routes

import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.latestbit.slack.morphism.client.{SlackApiClientT, SlackApiToken}
import org.latestbit.slack.morphism.client.reqresp.events.SlackApiEventMessageReply
import org.latestbit.slack.morphism.common.SlackResponseTypes
import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import bot.poll.simple.templates.{SlackModalTemplateExample, SlackSampleMessageReplyTemplateExample}
import org.latestbit.slack.morphism.client.reqresp.views.SlackApiViewsOpenRequest

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
            (form.getFirst("text"), form.getFirst("response_url")) match {
              case (Some(text), Some(responseUrl)) => {

                val commandReply = new SlackSampleMessageReplyTemplateExample(text)

                slackApiClient.events
                  .reply(
                    response_url = responseUrl,
                    SlackApiEventMessageReply(
                      text = commandReply.renderPlainText(),
                      blocks = commandReply.renderBlocks(),
                      response_type = Some(SlackResponseTypes.Ephemeral)
                    )
                  )
                  .flatMap { resp =>
                    resp.leftMap(err => logger.error(err.getMessage()))
                    Ok()
                  }
              }
              case _ => {
                BadRequest()
              }
            }
          }
        }
      }
    }
  }

}
