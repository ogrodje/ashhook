package si.ogrodje.ashhook

import si.ogrodje.ashhook.config.{AppConfig, DiscordWebhookConfig, WebhookConfig}
import zio.http.{Body, Client, MediaType}
import zio.stream.ZSink
import zio.{Scope, ZIO}
import zio.json.*

type MessageEncoder[C <: WebhookConfig] = C => MailMessage => Either[Throwable, String]
object MessageEncoders:
  given forDiscord: MessageEncoder[DiscordWebhookConfig] = config =>
    mailMessage =>
      val message = mailMessage.message
      Right(
        Map(
          "username" -> "ashhook",
          "content"  ->
            s"""✉️: **${message.subject}**
               |From: ${message.from}""".stripMargin
        ).toJson
      )

object WebhookForwarder:
  import MessageEncoders.given

  private def encode[C <: WebhookConfig](webhookConfig: C, message: MailMessage)(using
    encoder: MessageEncoder[C]
  ): ZIO[Any, Throwable, String] = {
    ZIO.from(encoder(webhookConfig)(message))
  }

  def sink: ZSink[Scope & Client & AppConfig, Throwable, MailMessage, Nothing, Unit] =
    val clientZio = for
      webhookConfig <- ZIO.serviceWith[AppConfig](_.webhookConfig.asInstanceOf[DiscordWebhookConfig])
      client        <- ZIO.serviceWith[Client](_.url(webhookConfig.url))
    yield client -> webhookConfig

    ZSink
      .fromZIO(clientZio)
      .flatMap((client, config) =>
        ZSink.foreach { (mailMessage: MailMessage) =>
          for
            message <- encode(config, mailMessage).tap(m => ZIO.logInfo(s"Encoded payload as:\n${m}"))
            _       <-
              client.post("")(Body.fromString(message).contentType(MediaType.application.json)).ignoreLogged
          yield ()
        }
      )
