package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import org.json.JSONObject

import jp.juggler.subwaytooter.util.parseString

class TootApplication(parser: TootParser, src : JSONObject){
	val name : String?
	@Suppress("unused") private val website : String?
//	val description : String?
	
	init{
		if( parser.serviceType==ServiceType.MISSKEY){
			name = src.parseString("name")
			website = null
//			description = src.parseString("description")
		}else{
			name = src.parseString("name")
			website = src.parseString("website")
//			description = website
		}
	}
}
