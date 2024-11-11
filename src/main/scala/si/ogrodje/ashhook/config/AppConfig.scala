package si.ogrodje.ashhook.config
import zio.{Task, ZIO, ZLayer}
import zio.http.URL

import scala.util.control.NoStackTrace

enum WebhookConfigError(message: String) extends Throwable with NoStackTrace:
  case MissingWebhookURL(key: String)           extends WebhookConfigError(s"Webhook environment variable ${key} is missing")
  case URLDecodingError(message: String)        extends WebhookConfigError(message)
  case UnsupportedWebhookPlatform(host: String) extends WebhookConfigError(s"Unsupported webhook platform: ${host}")

sealed trait WebhookConfig:
  def url: URL
object WebhookConfig:
  import WebhookConfigError.*
  def loadFromEnvironment: ZIO[Any, WebhookConfigError, WebhookConfig] = for
    webhookUrl    <- zio.System.env("WEBHOOK_URL").flatMap(ZIO.fromOption(_)).orElseFail(MissingWebhookURL("WEBHOOK_URL"))
    url           <- ZIO.fromEither(URL.decode(webhookUrl)).mapError(ex => URLDecodingError(ex.getMessage))
    webhookConfig <- if url.host.contains("discord.com") then DiscordWebhookConfig.loadWithURL(url)
                     else ZIO.fail(UnsupportedWebhookPlatform(url.host.getOrElse("UNKNOWN")))
  yield webhookConfig

final case class DiscordWebhookConfig private (
  url: URL
) extends WebhookConfig
object DiscordWebhookConfig:
  def loadWithURL(url: URL): ZIO[Any, WebhookConfigError, WebhookConfig] =
    ZIO.succeed(
      DiscordWebhookConfig(url)
    )

final case class AppConfig(
  mailServerConfig: MailServerConfig,
  webhookConfig: WebhookConfig
)

object AppConfig:
  def fromEnvironment: ZIO[Any, Throwable, AppConfig] = for
    mailServerConfig <- MailServerConfig.fromEnvironment
    webhookConfig    <- WebhookConfig.loadFromEnvironment
  yield AppConfig(
    mailServerConfig,
    webhookConfig
  )

  def live: ZLayer[Any, Throwable, AppConfig] = ZLayer.fromZIO(fromEnvironment)
