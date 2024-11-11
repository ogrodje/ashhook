package si.ogrodje.ashhook

import eu.timepit.refined.auto.autoUnwrap
import org.eclipse.angus.mail.imap.{IMAPFolder, SortTerm}
import jakarta.mail.{Folder, Message, Session, Store}
import si.ogrodje.ashhook.config.{MailServerConfig, Protocol}

import scala.util.Try

object JakartaOps:
  private given Conversion[Folder, IMAPFolder] = _.asInstanceOf[IMAPFolder]

  extension (session: Session)
    def tryGetStore(protocol: Protocol): Try[Store] =
      Try(session.getStore(protocol.name))

  extension (store: Store)
    def tryConnect(config: MailServerConfig): Try[Store] =
      Try {
        store.connect(
          config.host,
          config.port,
          config.username,
          config.password
        )
        store
      }

    def tryGetFolder(name: String): Try[IMAPFolder] = Try(store.getFolder(name))

  extension (folder: IMAPFolder)
    def tryOpenIt(mode: Int): Try[IMAPFolder] = Try {
      folder.open(mode)
      folder
    }

    def tryGetSortedMessages(filter: SortTerm*): Try[Array[Message]] = Try {
      folder.getSortedMessages(filter.toArray)
    }
