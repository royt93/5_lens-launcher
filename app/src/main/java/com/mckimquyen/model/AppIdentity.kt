package com.mckimquyen.model

/**
 * FEAT-012: the single place that builds and reads an app's per-user key.
 *
 * The primary user (id 0) keeps the exact key this app has always used (`package-component`), so Room rows,
 * backups and search history written before work-profile support stay valid with no migration. An app that lives
 * in another profile gets `@u<userId>` appended, so the same app installed in two profiles never collides.
 *
 * Known limitation: the suffix is a naming convention, not a column. A primary-user component whose class name
 * literally ends in `@u<digits>` would look like a profile app; [collides] lets callers detect that pairing.
 */
object AppIdentity {
    const val PRIMARY_USER_ID = 0
    private const val SUFFIX_PREFIX = "@u"
    private val SUFFIX = Regex("""@u(\d+)$""")

    @JvmStatic
    fun identifier(packageName: String?, name: String?, userId: Int = PRIMARY_USER_ID): String {
        val base = AppPersistent.generateIdentifier(packageName, name)
        return if (base.isEmpty()) "" else base + suffixFor(userId)
    }

    /** Key used by search history: `package/component`, suffixed like [identifier]. */
    @JvmStatic
    fun componentKey(packageName: String?, name: String?, userId: Int = PRIMARY_USER_ID): String =
        "$packageName/$name" + suffixFor(userId)

    /** Per-user variant of a bitmap-cache key; unchanged for the primary user. */
    @JvmStatic
    fun cacheKeySegment(key: String, userId: Int): String = key + suffixFor(userId)

    /** The user an [identifier] belongs to; anything without a well-formed suffix is the primary user. */
    @JvmStatic
    fun userIdOf(identifier: String): Int =
        SUFFIX.find(identifier)?.groupValues?.get(1)?.toIntOrNull() ?: PRIMARY_USER_ID

    /** The identifier with any user suffix removed, or null when nothing is left. */
    @JvmStatic
    fun stripUser(identifier: String): String? =
        identifier.replace(SUFFIX, "").takeIf { it.isNotEmpty() }

    /** True when two different (package, component, user) triples would produce the same identifier. */
    @JvmStatic
    fun collides(
        packageA: String, nameA: String, userA: Int,
        packageB: String, nameB: String, userB: Int
    ): Boolean = (packageA != packageB || nameA != nameB || userA != userB) &&
        identifier(packageA, nameA, userA) == identifier(packageB, nameB, userB)

    private fun suffixFor(userId: Int): String {
        require(userId >= 0) { "userId must not be negative: $userId" }
        return if (userId == PRIMARY_USER_ID) "" else "$SUFFIX_PREFIX$userId"
    }
}
