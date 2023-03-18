package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.CancellationException
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

private val log = LogCategory("WebFinger")

private fun NodeList.items() =
    (0 until length).mapNotNull { item(it) }

suspend fun TootApiClient.getApiHostFromWebFinger(apDomain: Host): Host? {
    val (result, bytes) = this.getHttpBytes("https://${apDomain.ascii}/.well-known/host-meta")
    result ?: throw CancellationException()
    result.error?.notEmpty()?.let { error(it) }
    bytes ?: error("getApiHostFromWebFinger: missing response body.")

    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(ByteArrayInputStream(bytes))

    val hostSet = document.getElementsByTagName("Link")
        .items()
        .filter { "lrdd" == it.attributes?.getNamedItem("rel")?.nodeValue }
        .mapNotNull { it.attributes?.getNamedItem("template")?.nodeValue?.mayUri()?.authority?.notEmpty() }
        .map { Host.parse(it) }
        .toSet()

    return when (hostSet.size) {
        1 -> hostSet.first()
        0 -> {
            log.e("can't find api host for domain ${apDomain.pretty} .")
            null
        }
        else -> {
            log.e("multiple hosts found for domain ${apDomain.pretty} . ${hostSet.joinToString(", ") { it.pretty }}")
            null
        }
    }
}
