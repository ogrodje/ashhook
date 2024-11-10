package si.ogrodje.ashhook
import jakarta.mail.event.{ConnectionEvent, ConnectionListener, MessageCountEvent, MessageCountListener}
import jakarta.mail.{Folder, Session}
import org.eclipse.angus.mail.imap.IMAPFolder
import zio.ZIO.logInfo
import zio.{Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}
import eu.timepit.refined.auto.autoUnwrap
import zio.logging.backend.SLF4J

import javax.net.ssl.{SSLContext, X509TrustManager}

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Nothing, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def program = for
    config <- ZIO.service[SMTPConfig]
    _      <- logInfo("This is main")
    _      <- logInfo(s"Config: ${config}")
    // _      <- SMTPMailFetcher.run(config)
    _      <- EmailStream
                .make(config)
                .tap(m =>
                  logInfo(
                    s"✉️ [${m.flags.mkString(", ")}][${m.messageID}] ${m.subject} from ${m.from}"
                  )
                )
                .runDrain
  yield ()

  override def run = program.provide(SMTPConfig.live.and(Scope.default))
