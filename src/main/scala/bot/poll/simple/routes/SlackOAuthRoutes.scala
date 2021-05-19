package bot.poll.simple.routes

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import org.latestbit.slack.morphism.client.{SlackApiClientError, SlackApiClientT}
import org.latestbit.slack.morphism.common.SlackApiError

import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb

class SlackOAuthRoutes[F[_]: Sync](
  slackApiClient: SlackApiClientT[F],
  tokensDb: SlackTokensDb[F],
  config: AppConfig
) extends StrictLogging {

  private val SLACK_AUTH_URL_V2 = uri"https://slack.com/oauth/v2/authorize"

  def routes(): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    val basePath = Path("auth")

    object QueryParamsMatchers {
      object QueryCodeParam extends OptionalQueryParamDecoderMatcher[String]("code")
      object ErrorCodeParam extends OptionalQueryParamDecoderMatcher[String]("error")
    }

    import QueryParamsMatchers._

    HttpRoutes.of[F] {
      case GET -> Root / basePath / "install" => {
        val baseParams: Map[String, String] = List[(String, Option[String])](
          "client_id"    -> Option(config.slackAppConfig.clientId),
          "scope"        -> Option(config.slackAppConfig.botScope),
          "redirect_uri" -> config.slackAppConfig.redirectUrl
        ).flatMap { case (k, v) => v.map(k -> _) }.toMap

        for {
          resp <- TemporaryRedirect(Location(SLACK_AUTH_URL_V2.withQueryParams(baseParams)))
        } yield resp
      }

      case GET -> Root / basePath / "callback" :? QueryCodeParam(code) +& ErrorCodeParam(error) => {
        (code, error) match {
          case (Some(oauthCode), _) => {
            logger.info(s"Received OAuth access code: ${oauthCode}")
            EitherT(
              slackApiClient.oauth.v2.access(
                clientId = config.slackAppConfig.clientId,
                clientSecret = config.slackAppConfig.clientSecret,
                code = oauthCode,
                redirectUri = config.slackAppConfig.redirectUrl
              )
            ).flatMap { tokens =>
              logger.info(s"Received OAuth access tokens: ${tokens}")
              EitherT(
                tokensDb
                  .insertToken(
                    teamId = tokens.team.id,
                    SlackTokensDb.TokenRecord(
                      tokenType = tokens.token_type,
                      scope = tokens.scope,
                      tokenValue = tokens.access_token,
                      userId = tokens.bot_user_id.getOrElse(tokens.authed_user.id)
                    )
                  )
                  .map(_.asRight[SlackApiError])
              )
            }.value.flatMap {
              case Right(_) => {
                Ok("Installed")
              }
              case Left(err) => {
                logger.info(s"OAuth access error : ${err}")
                InternalServerError()
              }
            }
          }
          case (_, Some(error)) => {
            logger.info(s"OAuth error code: ${error}")
            Ok()
          }
          case _ => {
            logger.error(s"No OAuth code or error provided?")
            InternalServerError()
          }
        }
      }
    }
  }

}
