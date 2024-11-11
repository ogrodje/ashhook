package si.ogrodje.ashhook.config

import eu.timepit.refined.*
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.boolean.*
import eu.timepit.refined.collection.*
import eu.timepit.refined.numeric.*
import eu.timepit.refined.string.*
import jakarta.mail.Folder
import si.ogrodje.ashhook.config.MailServerConfig.*
import si.ogrodje.ashhook.config.Protocol.IMAPS
import si.ogrodje.ashhook.config.{MailServerConfigError, Protocol}
import zio.{RIO, ZIO, ZLayer}

import scala.util.control.NoStackTrace

enum Protocol(val name: String):
  case IMAP  extends Protocol("imap")
  case IMAPS extends Protocol("imaps")

final case class MailServerConfig private (
  host: Host,
  username: Username,
  password: Password,
  port: Port,
  protocol: Protocol = IMAPS,
  markAsSeen: Boolean = false
):
  def folderMode: Int           = if markAsSeen then Folder.READ_WRITE else Folder.READ_ONLY
  override def toString: String =
    s"MailServerConfig($host, $username, ${password.value.take(4)}..., ${protocol}, ${port})"

enum MailServerConfigError(message: String) extends RuntimeException(message) with NoStackTrace:
  case ValidationError(message: String)      extends MailServerConfigError(message)
  case EnvironmentReadingError(name: String) extends MailServerConfigError(s"Failed reading environment variable $name")

object MailServerConfig:
  import MailServerConfigError.*

  private type Host     = String Refined NonEmpty
  private type Username = String Refined NonEmpty
  private type Password = String Refined NonEmpty
  private type Port     = Int Refined Positive

  private def load(
    rawHostname: String,
    rawUsername: String,
    rawPassword: String,
    rawPort: String
  ): Either[String, MailServerConfig] = for
    hostname <- refineV[NonEmpty](rawHostname)
    username <- refineV[NonEmpty](rawUsername)
    password <- refineV[NonEmpty](rawPassword)
    port     <- refineV[Positive](rawPort.toInt)
  yield apply(
    hostname,
    username,
    password,
    port
  )

  def loadZIO(
    hostname: String,
    username: String,
    password: String,
    port: String
  ): ZIO[Any, ValidationError, MailServerConfig] =
    ZIO.fromEither(load(hostname, username, password, port)).mapError(ValidationError.apply)

  private def readRequiredEnv(key: String): ZIO[Any, EnvironmentReadingError, String] =
    zio.System.env(key).flatMap(ZIO.fromOption(_)).orElseFail(EnvironmentReadingError(key))

  def fromEnvironment: ZIO[Any, MailServerConfigError, MailServerConfig] = for
    hostname <- readRequiredEnv("IMAP_HOSTNAME")
    username <- readRequiredEnv("IMAP_USERNAME")
    password <- readRequiredEnv("IMAP_PASSWORD")
    port     <- readRequiredEnv("IMAP_PORT")
    config   <- loadZIO(hostname, username, password, port)
  yield config

  def live: ZLayer[Any, Throwable, MailServerConfig] = ZLayer.fromZIO(fromEnvironment)
