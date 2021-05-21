package jp.juggler.subwaytooter.api.entity

object SuggestionSource {

    val map = HashMap<Long,HashMap<Acct,String>>()

    fun set(db_id:Long?, acct:Acct, source:String?){
        synchronized(this){
            db_id ?: return
            source ?: return
            var m2 = map[db_id]
            if(m2 ==null){
               m2 = HashMap()
               map[db_id] = m2
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