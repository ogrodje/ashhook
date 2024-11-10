package si.ogrodje.ashhook

import javax.net.ssl.{SSLContext, X509TrustManager}
import java.security.cert.X509Certificate

object TrustAllX509TrustManager extends X509TrustManager:
  private type Certificates = Array[X509Certificate]
  def checkClientTrusted(chain: Certificates, authType: String): Unit = ()
  def checkServerTrusted(chain: Certificates, authType: String): Unit = ()
  def getAcceptedIssuers: Certificates                                = null

  def trustAllCertificates(): Unit =
    val sslTrustContext: SSLContext = SSLContext.getInstance("TLS")
    sslTrustContext.init(null, Array(TrustAllX509TrustManager), new java.security.SecureRandom())
    SSLContext.setDefault(sslTrustContext)
