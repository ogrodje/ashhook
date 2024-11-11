package si.ogrodje.ashhook

import eu.timepit.refined.auto.autoUnwrap
import jakarta.mail.event.{MessageCountEvent, MessageCountListener}
import jakarta.mail.{Message, Session, Store}
import org.eclipse.angus.mail.imap.{IMAPFolder, SortTerm}
import si.ogrodje.ashhook.config.MailServerConfig
import zio.ZIO.{attempt, executor, fromTry, logInfo}
import zio.stream.{Stream, ZStream}
import zio.{Chunk, RIO, Scope, Task, ZIO}
import zio.durationInt

final case class MessageID(
  uid: Option[Long],        // IMAP UID
  messageID: Option[String] // RFC 822 - Message ID Header
)

final case class EmailMessage(
  messageID: MessageID,
  subject: String,
  from: String,
  content: String,
  flags: Set[SafeFlags]
)

object EmailStream:
  import JakartaOps.{*, given}
  import FlagOps.*

  private def setupSSL: Task[Unit]         = attempt(TrustAllX509TrustManager.trustAllCertificates())
  private def createSession: Task[Session] = attempt(Session.getInstance(System.getProperties, null))

  private def connectStore(config: MailServerConfig, session: Session): RIO[Scope, Store] =
    ZIO.fromAutoCloseable(fromTry:
      session.tryGetStore(config.protocol).flatMap(_.tryConnect(config))
    )

  private def openFolder(config: MailServerConfig, store: Store, name: String = "INBOX"): RIO[Scope, IMAPFolder] =
    ZIO.fromAutoCloseable(fromTry:
      store.tryGetFolder(name).flatMap(_.tryOpenIt(config.folderMode))
    )

  private def getMessageID(folder: IMAPFolder, message: Message): Task[MessageID] = attempt:
    MessageID(
      uid = Option(folder.getUID(message)).flatMap(u => if u > 0 then Some(u) else None),
      messageID = Option(message.getHeader("Message-ID")).flatMap(_.headOption)
    )

  private def parseEmailMessage(folder: IMAPFolder)(message: Message): Task[EmailMessage] = for
    messageID   <- getMessageID(folder, message)
    emailMessage = EmailMessage(
                     messageID = messageID,
                     subject = message.getSubject,
                     from = message.getFrom.headOption.map(_.toString).getOrElse("UNKNOWN"),
                     content = message.getContent.toString,
                     flags = message.getFlags.getSystemFlags.toSet.map(_.asSafeFlag)
                   )
  yield emailMessage

  private def existingStream(
    config: MailServerConfig,
    folder: IMAPFolder,
    top: Int = 100
  ): Stream[Throwable, EmailMessage] =
    ZStream
      .fromZIO(fromTry(folder.tryGetSortedMessages(SortTerm.REVERSE, SortTerm.DATE)))
      .take(top)
      .flatMap(ZStream.fromIterable)
      .mapZIO(parseEmailMessage(folder))

  private def monitoringStream(config: MailServerConfig, folder: IMAPFolder): Stream[Throwable, EmailMessage] =
    ZStream.logInfo(s"Starting monitoring of ${folder.getName} for new messages") *>
      ZStream.asyncZIO[Any, Throwable, EmailMessage]: callback =>
        val listener = new MessageCountListener:
          def messagesRemoved(event: MessageCountEvent): Unit = ()
          def messagesAdded(event: MessageCountEvent): Unit   = event.getMessages.foreach: message =>
            callback(parseEmailMessage(folder)(message).mapBoth(Some(_), Chunk(_)))

        folder.addMessageCountListener(listener)

        ZIO.attemptBlocking {
          var supportsIdle = false
          try
            folder.idle() // Attempt to enter IDLE mode
            supportsIdle = true
          catch
            case _: Exception => supportsIdle = false // Fallback to polling if IDLE isn't supported

            // Fallback to interval pooling with 200ms delay
          if supportsIdle then folder.idle()
          else (ZIO.attemptBlocking(folder.getMessageCount) *> ZIO.sleep(200.millis)).forever.fork
        }.fork

  def observe(config: MailServerConfig): ZStream[Scope, Throwable, MailMessage] = ZStream.scoped {
    for
      _       <- setupSSL
      session <- createSession
      store   <- connectStore(config, session)
      inbox   <- openFolder(config, store)
    yield monitoringStream(config, inbox).map(MailMessage.Fresh(_))
  }.flatten

  def streamAll(config: MailServerConfig, top: Int = 100): ZStream[Scope, Throwable, MailMessage] = ZStream.scoped {
    for
      _       <- setupSSL
      session <- createSession
      store   <- connectStore(config, session)
      inbox   <- openFolder(config, store)
    yield
      val existing   = existingStream(config, inbox, top).map(MailMessage.Existing(_))
      val monitoring = monitoringStream(config, inbox).map(MailMessage.Fresh(_))
      existing ++ monitoring
  }.flatten

enum MailMessage(val message: EmailMessage):
  case Existing(override val message: EmailMessage) extends MailMessage(message)
  case Fresh(override val message: EmailMessage)    extends MailMessage(message)
