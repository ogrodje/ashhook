package si.ogrodje.ashhook

import eu.timepit.refined.auto.autoUnwrap
import jakarta.mail.event.{MessageCountEvent, MessageCountListener}
import jakarta.mail.{Message, Session, Store}
import org.eclipse.angus.mail.imap.{IMAPFolder, SortTerm}
import zio.ZIO.attempt
import zio.stream.{Stream, ZStream}
import zio.{Chunk, RIO, Scope, Task, ZIO}

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
  import FlagOps.*
  private def setupSSL: Task[Unit]         = attempt(TrustAllX509TrustManager.trustAllCertificates())
  private def createSession: Task[Session] = attempt(Session.getInstance(System.getProperties, null))

  private def connectStore(config: SMTPConfig, session: Session): RIO[Scope, Store] = ZIO.fromAutoCloseable(attempt:
    val store = session.getStore("imaps")
    store.connect(config.host, config.port, config.username, config.password)
    store
  )

  private def openInbox(config: SMTPConfig, store: Store): RIO[Scope, IMAPFolder] = ZIO.fromAutoCloseable(attempt:
    val inbox = store.getFolder("INBOX").asInstanceOf[IMAPFolder]
    inbox.open(config.folderMode)
    inbox
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

  private def fetchExisting(config: SMTPConfig, folder: IMAPFolder, top: Int = 100): Stream[Throwable, EmailMessage] =
    ZStream
      .fromZIO(attempt(folder.getSortedMessages(Array(SortTerm.REVERSE, SortTerm.DATE))))
      .take(top)
      .flatMap(ZStream.fromIterable)
      .mapZIO(parseEmailMessage(folder))

  private def observeForNew(config: SMTPConfig, folder: IMAPFolder): Stream[Throwable, EmailMessage] =
    ZStream.logInfo("Starting monitoring of new messages") *>
      ZStream.asyncZIO[Any, Throwable, EmailMessage]: callback =>
        val listener = new MessageCountListener:
          def messagesRemoved(event: MessageCountEvent): Unit = ()
          def messagesAdded(event: MessageCountEvent): Unit   = event.getMessages.foreach: message =>
            callback(parseEmailMessage(folder)(message).mapBoth(Some(_), Chunk(_)))

        folder.addMessageCountListener(listener)

        // TODO: Could this be attemptBlocking without fork?
        attempt {
          var supportsIdle = false
          try
            folder.idle(); supportsIdle = true
          finally supportsIdle = false

          while true do
            if supportsIdle then folder.idle()
            else
              Thread.sleep(200L); folder.getMessageCount
        }.fork

  def make(config: SMTPConfig, top: Int = 100): ZStream[Scope, Throwable, EmailMessage] = ZStream.acquireReleaseWith {
    for
      _       <- setupSSL
      session <- createSession
      store   <- connectStore(config, session)
      inbox   <- openInbox(config, store)
    yield store -> inbox
  }(_ => ZIO.none).flatMap((store, inbox) =>
    fetchExisting(config, inbox, top) ++
      observeForNew(config, inbox)
  )
