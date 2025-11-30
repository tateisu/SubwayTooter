package jp.juggler.util.network

import jp.juggler.util.log.LogCategory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.KeyStore
import javax.net.ssl.*

object MySslSocketFactory : SSLSocketFactory() {

    private val log = LogCategory("MySslSocketFactory")

    private var debugCipherSuites = false

    private val originalFactory: SSLSocketFactory =
        SSLContext.getInstance("TLS").apply {
            init(null, null, null)
        }.socketFactory

    private fun check(socket: Socket?): Socket? {
        // 端末のデフォルトでは1.3が含まれないので追加する
        (socket as? SSLSocket)?.enabledProtocols = arrayOf("TLSv1.1", "TLSv1.2", "TLSv1.3")

        // デバッグフラグが変更された後に１回だけ、ソケットの暗号化スイートを列挙する
        if (debugCipherSuites) {
            debugCipherSuites = false
            (socket as? SSLSocket)?.enabledCipherSuites?.forEach { cs ->
                log.d("getEnabledCipherSuites : $cs")
            }
        }

        return socket
    }

    override fun getDefaultCipherSuites(): Array<String> =
        originalFactory.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> =
        originalFactory.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket(): Socket? =
        check(originalFactory.createSocket())

    @Throws(IOException::class)
    override fun createSocket(
        s: Socket,
        host: String,
        port: Int,
        autoClose: Boolean,
    ): Socket? = check(
        originalFactory.createSocket(
            s,
            host,
            port,
            autoClose
        )
    )

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(
        host: String,
        port: Int,
    ): Socket? = check(
        originalFactory.createSocket(
            host,
            port
        )
    )

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket? = check(
        originalFactory.createSocket(
            host,
            port,
            localHost,
            localPort
        )
    )

    @Throws(IOException::class)
    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket? = check(
        originalFactory.createSocket(
            host,
            port
        )
    )

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket? = check(
        originalFactory.createSocket(
            address,
            port,
            localAddress,
            localPort
        )
    )

    // App1, TestTootInstance 等で使われる
    val trustManager: X509TrustManager by lazy {
        val list = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers

        list?.firstNotNullOfOrNull { it as? X509TrustManager }
            ?: error("missing X509TrustManager in $list")
    }
}
