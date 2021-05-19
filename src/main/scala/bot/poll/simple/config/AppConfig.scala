package bot.poll.simple.config

/**
  * Example config model
  */
case class AppConfig(
  httpServerHost: String = AppConfig.defaultHost,
  httpServerPort: Int = AppConfig.defaultPort,
  slackAppConfig: SlackAppConfig,
  databaseDir: String = AppConfig.defaultDatabaseDir
)

object AppConfig {
  final val defaultHost        = "0.0.0.0"
  final val defaultPort        = 8080
  final val defaultDatabaseDir = "data"
}
