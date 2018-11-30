@file:Suppress("unused")

package jp.juggler.subwaytooter.util

import jp.juggler.util.LogCategory
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

object DomXmlUtils {
	val log = LogCategory("DomXmlUtils")
	
	val xml_builder : DocumentBuilder by lazy {
		DocumentBuilderFactory.newInstance().newDocumentBuilder()
	}
}

fun ByteArray.parseXml() : Element? {
	return try {
		DomXmlUtils.xml_builder.parse(ByteArrayInputStream(this)).documentElement
	} catch(ex : Throwable) {
		DomXmlUtils.log.trace(ex)
		null
	}
}

fun NamedNodeMap.getAttribute(name : String, defVal : String?) : String? {
	return this.getNamedItem(name)?.nodeValue ?: defVal
}