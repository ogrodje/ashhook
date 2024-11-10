package si.ogrodje.ashhook
import eu.timepit.refined.*
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.boolean.*
import eu.timepit.refined.collection.*
import eu.timepit.refined.numeric.*
import eu.timepit.refined.string.*
import si.ogrodje.ashhook.SMTPConfig.*
import zio.{RIO, ZIO, ZLayer}
import jakarta.mail.Folder
import scala.util.control.NoStackTrace

final case class SMTPConfig private (
  host: Host,
  username: Username,
  password: Password,
  port: Port,
  markAsSeen: Boolean = false
):
  def folderMode: Int           = if markAsSeen then Folder.READ_WRITE else Folder.READ_ONLY
  override def toString: String = s"SMTPConfig($host, $username, ${password.value.take(4)}..., ${port})"

enum SMTPConfigError(message: String) extends RuntimeException(message) with NoStackTrace:
  case ValidationError(message: String)      extends SMTPConfigError(message)
  case EnvironmentReadingError(name: String) extends SMTPConfigError(s"Failed reading environment variable $name")

object SMTPConfig:
  import SMTPConfigError.*

  private type Host     = String Refined NonEmpty
  private type Username = String Refined NonEmpty
  private type Password = String Refined NonEmpty
  private type Port     = Int Refined Positive

  private def load(
    rawHostname: String,
    rawUsername: String,
    rawPassword: String,
    rawPort: String
  ): Either[String, SMTPConfig] = for
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
  ): ZIO[Any, ValidationError, SMTPConfig] =
    ZIO.fromEither(load(hostname, username, password, port)).mapError(ValidationError.apply)

  private def readRequiredEnv(key: String): ZIO[Any, EnvironmentReadingError, String] =
    zio.System.env(key).flatMap(ZIO.fromOption(_)).orElseFail(EnvironmentReadingError(key))

  private def fromEnvironment: ZIO[Any, SMTPConfigError, SMTPConfig] = for
    hostname <- readRequiredEnv("SMTP_HOSTNAME")
    username <- readRequiredEnv("SMTP_USERNAME")
    password <- readRequiredEnv("SMTP_PASSWORD")
    port     <- readRequiredEnv("SMTP_PORT")
    config   <- loadZIO(hostname, username, password, port)
  yield config

  def live: ZLayer[Any, Throwable, SMTPConfig] = ZLayer.fromZIO(fromEnvironment)
