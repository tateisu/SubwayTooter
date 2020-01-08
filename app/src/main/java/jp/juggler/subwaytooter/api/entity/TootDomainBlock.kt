package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonArray
import java.util.ArrayList

class TootDomainBlock(
	val domain : String
):TimelineItem() {

	companion object {
		fun parseList(array : JsonArray?) : ArrayList<TootDomainBlock> {
			val result = ArrayList<TootDomainBlock>()
			if(array != null) {
				result.ensureCapacity(array.size)
				array.stringList().forEach {
					if(it.isNotEmpty() ) {
						result.add(TootDomainBlock(it))
					}
				}
			}
			return result
		}
	}
	
}
