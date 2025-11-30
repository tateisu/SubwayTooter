package jp.juggler.subwaytooter.api.entity

object SuggestionSource {

    val map = HashMap<Long, HashMap<Acct, String>>()

    fun set(dbId: Long?, acct: Acct, source: String?) {
        synchronized(this) {
            dbId ?: return
            source ?: return
            var m2 = map[dbId]
            if (m2 == null) {
                m2 = HashMap()
                map[dbId] = m2
            }
            m2[acct] = source
        }
    }

    fun get(dbId: Long, acct: Acct): String? {
        synchronized(this) {
            return map[dbId]?.get(acct)
        }
    }
}
