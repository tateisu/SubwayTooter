package jp.juggler.subwaytooter.api.entity

sealed class RelationshipSeveranceEventType(val code: String) {
    class Unknown(c: String) : RelationshipSeveranceEventType(c)
    data object DomainBlock : RelationshipSeveranceEventType("domain_block")
    data object UserDomainBlock : RelationshipSeveranceEventType("user_domain_block")
    data object AccountSuspension : RelationshipSeveranceEventType("account_suspension")

    companion object {
        val allKnown: List<RelationshipSeveranceEventType> by lazy {
            RelationshipSeveranceEventType::class.sealedSubclasses.mapNotNull { it.objectInstance }
        }

        val map by lazy {
            allKnown.associateBy { it.code }
        }

        fun String?.toRelationshipSeveranceEventType(): RelationshipSeveranceEventType =
            this?.let { map[it] } ?: Unknown(this ?: "(null)")
    }
}
