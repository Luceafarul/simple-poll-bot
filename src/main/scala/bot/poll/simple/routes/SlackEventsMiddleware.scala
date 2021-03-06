package bot.poll.simple.routes

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.parser._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.latestbit.slack.morphism.client.SlackApiToken
import org.latestbit.slack.morphism.events.signature._
import bot.poll.simple.config.AppConfig
import bot.poll.simple.db.SlackTokensDb
import org.latestbit.slack.morphism.common.SlackTeamId

trait SlackEventsMiddleware extends StrictLogging {

  private val slackSignatureVerifier = new SlackEventSignatureVerifier()

  private def verifySlackSignatureRequest[F[_]: Sync](
    config: AppConfig,
    req: Request[F]
  ): F[Either[SlackSignatureVerificationError, String]] = {
    req.bodyAsText.compile
      .fold("")(_ ++ _)
      .flatMap { body =>
        Sync[F].delay(
          (
            req.headers.get(SlackEventSignatureVerifier.HttpHeaderNames.SignedHash.ci),
            req.headers.get(SlackEventSignatureVerifier.HttpHeaderNames.SignedTimestamp.ci)
          ) match {
            case (Some(receivedHash), Some(signedTimestamp)) => {
              slackSignatureVerifier
                .verify(
                  config.slackAppConfig.signingSecret,
                  receivedHash.value,
                  signedTimestamp.value,
                  body
                )
                .map { _ =>
                  body
                }
            }
            case _ => SlackAbsentSignatureError("Absent HTTP headers required for a Slack signature").asLeft
          }
        )
      }
  }

  private def decodeVerifiedSlackEventBody[F[_]: Sync](
    config: AppConfig,
    req: Request[F]
  ) = {
    OptionT(
      verifySlackSignatureRequest[F](config, req).flatMap {
        case Right(body) => Sync[F].pure(body.some)
        case Left(err) =>
          Sync[F]
            .delay(logger.error("Error: {}", err))
            .map { _ =>
              Option.empty[String]
            }
      }
    )
  }

  protected def decodeJson[F[_]: Sync, J](body: String)(implicit decoder: Decoder[J]): OptionT[F, J] = {
    OptionT(
      Sync[F].delay {
        decode[J](body) match {
          case Right(decoded) => decoded.some
          case Left(err) => {
            logger.error(s"Decode error: {}", err)
            Option.empty[J]
          }
        }
      }
    )
  }

  protected def slackSignedRoutes[F[_]: Sync](
    req: Request[F]
  )(resp: => F[Response[F]])(implicit config: AppConfig): F[Response[F]] = {
    decodeVerifiedSlackEventBody[F](config, req).flatMapF { _ =>
      resp.map(_.some)
    }.getOrElseF(
      Sync[F].pure(
        Response[F](status = Forbidden)
      )
    )
  }

  protected def slackSignedRoutes[F[_]: Sync, J](
    req: Request[F]
  )(resp: J => F[Response[F]])(implicit config: AppConfig, decoder: Decoder[J]): F[Response[F]] = {
    decodeVerifiedSlackEventBody[F](config, req)
      .flatMap(decodeJson[F, J])
      .flatMapF { decoded =>
        resp(decoded).map(_.some)
      }
      .getOrElseF(
        Sync[F].pure(
          Response[F](status = Forbidden)
        )
      )
  }

  protected def extractSlackWorkspaceToken[F[_]: Sync](
    workspaceId: SlackTeamId
  )(
    resp: SlackApiToken => F[Response[F]]
  )(implicit tokensDb: SlackTokensDb[F]): F[Response[F]] =
    OptionT(
      tokensDb
        .readTokens(workspaceId)
        .map { tokensRecord =>
          tokensRecord.flatMap(record =>
            record.tokens.lastOption.map { lastToken =>
              SlackApiToken.createFrom(
                tokenType = lastToken.tokenType,
                tokenValue = lastToken.tokenValue,
                scope = Some(lastToken.scope),
                teamId = Some(workspaceId)
              )
          })
        }
    ).flatMapF { token =>
      resp(token).map(_.some)
    }.getOrElseF {
      Sync[F]
        .delay(logger.warn("Token absent for: {}", workspaceId))
        .map(_ => Response[F](status = Ok))
    }

}
