package si.ogrodje.ashhook
import jakarta.mail.event.{ConnectionEvent, ConnectionListener, MessageCountEvent, MessageCountListener}
import jakarta.mail.{Folder, Session}
import org.eclipse.angus.mail.imap.IMAPFolder
import zio.ZIO.logInfo
import zio.{Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}
import eu.timepit.refined.auto.autoUnwrap
import zio.logging.backend.SLF4J
import MailMessage.*
import si.ogrodje.ashhook.config.{AppConfig, MailServerConfig}
import zio.stream.{ZSink, ZStream}
import zio.http.{Body, Client, MediaType}
import zio.json.*

import java.io.IOException
import java.nio.charset.{Charset, StandardCharsets}
import javax.net.ssl.{SSLContext, X509TrustManager}

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Nothing, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def program = for
    appConfig     <- ZIO.service[AppConfig]
    _             <- logInfo("This is main")
    _             <- logInfo(s"Config: ${appConfig}")
    mailStreamFib <-
      EmailStream
        .observe(appConfig.mailServerConfig)
        .tap {
          case Existing(message) =>
            logInfo(
              s"✉️ [${message.flags.mkString(", ")}][${message.messageID}] ${message.subject} from ${message.from}"
            )
          case Fresh(message)    =>
            logInfo(
              s"✨ [${message.flags.mkString(", ")}][${message.messageID}] ${message.subject} from ${message.from}"
            )
        }
        .run(WebhookForwarder.sink) // Actually emits.
        // .runDrain
        .fork
    _             <- mailStreamFib.join
  yield ()

  override def run = program.provide(Client.default, AppConfig.live.and(Scope.default))
