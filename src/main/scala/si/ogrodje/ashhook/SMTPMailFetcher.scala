package si.ogrodje.ashhook

import jakarta.mail.event.{ConnectionEvent, ConnectionListener, MessageCountEvent, MessageCountListener}
import jakarta.mail.{Folder, Session}
import org.eclipse.angus.mail.imap.IMAPFolder
import zio.{Task, ZIO}
import eu.timepit.refined.auto.autoUnwrap

import javax.net.ssl.SSLContext

object SMTPMailFetcher:
  // Source: https://jakarta.ee/specifications/mail/2.1/jakarta-mail-spec-2.1#example-monitoring-a-mailbox

  def run(config: SMTPConfig): Task[Unit] = ZIO.attemptBlocking {
    val SMTPConfig(hostname, username, password, port, _) = config

    TrustAllX509TrustManager.trustAllCertificates()

    // Set properties
    val session = Session.getInstance(System.getProperties, null)
    val store   = session.getStore("imaps")

    store.addConnectionListener(new ConnectionListener:
      override def opened(e: ConnectionEvent): Unit       = println("Opened.")
      override def disconnected(e: ConnectionEvent): Unit = println("Disconnected.")
      override def closed(e: ConnectionEvent): Unit       = println("Closed")
    )
    println("Connecting...")
    store.connect(hostname, port, username, password)

    println("--- Connected ---")
    // Go to INBOX
    val inbox: IMAPFolder = store.getFolder("INBOX").asInstanceOf[IMAPFolder]
    println(s"Inbox: ${inbox.getName}")

    // Add step here to get unseen messages

    inbox.addMessageCountListener(new MessageCountListener:
      override def messagesAdded(event: MessageCountEvent): Unit =
        println(s"Got ${event.getMessages.length} new messages.")
        event.getMessages.foreach { message =>
          println(s"✉️ \"${message.getSubject}\" from ${message.getFrom.headOption.getOrElse("UNKNOWN")}")

          // Mark message as seen.
          // message.setFlag(Flag.SEEN, true)
        }
      override def messagesRemoved(e: MessageCountEvent): Unit   = ()
    )

    var supportsIdle = false
    try
      inbox.open(Folder.READ_ONLY)
      inbox.idle()
      supportsIdle = true
    finally supportsIdle = false

    while true do
      if supportsIdle then
        inbox.idle()
        println("IDLE DONE")
      else
        Thread.sleep(200L)
        inbox.getMessageCount
  }
