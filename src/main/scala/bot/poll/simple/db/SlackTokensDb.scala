package bot.poll.simple.db

import cats.effect.{ConcurrentEffect, LiftIO, Resource}
import swaydb.data.slice.Slice
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import bot.poll.simple.config.AppConfig
import cats.effect._
import cats.effect.IO
import org.latestbit.slack.morphism.common.{SlackAccessTokenValue, SlackApiTokenScope, SlackApiTokenType, SlackChannelId, SlackTeamId, SlackUserId}
import swaydb.{IO => _, Set => _, _}
import swaydb.cats.effect.Tag._

import scala.concurrent.ExecutionContext.Implicits.global

class SlackTokensDb[F[_]: ConcurrentEffect](storage: SlackTokensDb.SwayDbType) extends StrictLogging {

  import SlackTokensDb._

  def insertToken(teamId: SlackTeamId, tokenRecord: TokenRecord): F[Unit] = {
    LiftIO[F].liftIO(
      storage
        .get(key = teamId)
        .map(
          _.map(rec =>
            rec.copy(tokens =
              rec.tokens.filterNot(_.userId == tokenRecord.userId) :+ tokenRecord)).getOrElse(
            TeamTokensRecord(
              teamId = teamId,
              tokens = List(
                tokenRecord
              )
            )
          )
        )
        .flatMap { record =>
          storage.put(teamId, record).map { _ =>
            logger.info(s"Inserting record for : ${teamId}/${tokenRecord.userId}")
          }
        }
    )
  }

  def removeTokens(teamId: SlackTeamId, users: Set[SlackUserId]): F[Unit] = {
    LiftIO[F].liftIO[Unit](
      storage
        .get(key = teamId)
        .flatMap(res =>
          res.map { record =>
            storage
              .put(
                teamId,
                record.copy(
                  tokens = record.tokens.filterNot(token => users.contains(token.userId))
                )
              )
              .map(_ => logger.info(s"Removed tokens for: ${users.mkString(",")}"))
          }.getOrElse(IO.unit))
    )
  }

  def readTokens(teamId: SlackTeamId): F[Option[TeamTokensRecord]] = {
    LiftIO[F].liftIO(
      storage.get(key = teamId)
    )
  }

  private def close(): F[Unit] = LiftIO[F].liftIO {
    IO(logger.info(s"Closing tokens database")).flatMap(_ => storage.close())
  }

}

object SlackTokensDb extends StrictLogging {
  case class TokenRecord(
    tokenType: SlackApiTokenType,
    tokenValue: SlackAccessTokenValue,
    userId: SlackUserId,
    scope: SlackApiTokenScope
  )

  case class TeamTokensRecord(teamId: SlackTeamId, tokens: List[TokenRecord])

  implicit object SlackChannelIdSerializer extends swaydb.serializers.Serializer[SlackChannelId] {

    import io.circe.parser._
    import io.circe.syntax._
    import io.circe.generic.auto._

    override def write(data: SlackChannelId): Slice[Byte] =
      Slice(data.asJson.dropNullValues.noSpaces.getBytes)

    override def read(data: Slice[Byte]): SlackChannelId =
      decode[SlackChannelId](new String(data.toArray)).valueOr(throw _)
  }

  implicit object SlackTeamIdSerializer extends swaydb.serializers.Serializer[SlackTeamId] {

    import io.circe.parser._
    import io.circe.syntax._
    import io.circe.generic.auto._

    override def write(data: SlackTeamId): Slice[Byte] =
      Slice(data.asJson.dropNullValues.noSpaces.getBytes)

    override def read(data: Slice[Byte]): SlackTeamId =
      decode[SlackTeamId](new String(data.toArray)).valueOr(throw _)
  }

  implicit object TeamTokensRecordSwayDbSerializer extends swaydb.serializers.Serializer[TeamTokensRecord] {

    import io.circe.parser._
    import io.circe.syntax._
    import io.circe.generic.auto._

    override def write(data: TeamTokensRecord): Slice[Byte] = {
      Slice(data.asJson.dropNullValues.noSpaces.getBytes)
    }

    override def read(data: Slice[Byte]): TeamTokensRecord = {
      decode[TeamTokensRecord](new String(data.toArray)).valueOr(throw _)
    }
  }

  private type FunctionType = PureFunction[SlackTeamId, TeamTokensRecord, Apply.Map[TeamTokensRecord]]
  private type SwayDbType   = swaydb.Map[SlackTeamId, TeamTokensRecord, FunctionType, IO]

  private def openDb[F[_]: ConcurrentEffect](config: AppConfig): F[SlackTokensDb[F]] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(global)

    implicitly[ConcurrentEffect[F]].delay {
      logger.info(s"Opening database in dir: '${config.databaseDir}''")
      persistent.Map[SlackTeamId, TeamTokensRecord, FunctionType, IO](dir = config.databaseDir).get
    }.map(storage => new SlackTokensDb[F](storage))
  }

  def open[F[_]: ConcurrentEffect](config: AppConfig): Resource[F, SlackTokensDb[F]] = {
    Resource.make[F, SlackTokensDb[F]](openDb[F](config))(_.close())
  }
}
