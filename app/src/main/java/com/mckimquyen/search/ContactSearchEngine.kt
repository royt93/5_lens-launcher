package com.mckimquyen.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mckimquyen.util.UtilSettings

data class ContactSearchResult(
    val displayName: String,
    val phoneNumber: String
) {
    fun dialIntent(): Intent = Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phoneNumber)}".toUri())

    fun messageIntent(): Intent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(phoneNumber)}".toUri())
}

internal data class ContactRow(
    val displayName: String,
    val phoneNumber: String
)

object ContactSearchEngine {
    private const val DEFAULT_LIMIT = 3
    private var testRows: List<ContactRow>? = null
    private var permissionGrantedForTesting: Boolean? = null

    private val CONTACT_PREFIXES = listOf(
        "contact",
        "contacts",
        "call",
        "message",
        "sms",
        "lien he",
        "goi",
        "nhan",
        "nhan tin"
    )

    @JvmStatic
    fun extractedQuery(query: CharSequence?): String {
        val raw = query?.toString()?.trim().orEmpty()
        val normalized = AppSearchEngine.normalize(raw)
        val prefix = CONTACT_PREFIXES.firstOrNull { normalized == it || normalized.startsWith("$it ") }
            ?: return ""
        return raw.drop(prefix.length).trim()
    }

    @JvmStatic
    fun isExplicitContactQuery(query: CharSequence?): Boolean = extractedQuery(query).length >= 2

    @JvmStatic
    fun hasContactsPermission(context: Context): Boolean =
        permissionGrantedForTesting ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED)

    @JvmStatic
    fun shouldShowPermissionRequest(context: Context, query: CharSequence?): Boolean {
        if (!isExplicitContactQuery(query)) return false
        if (hasContactsPermission(context)) return false
        return !UtilSettings(context).getBoolean(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED)
    }

    @JvmStatic
    fun firstContactForQuery(context: Context, query: CharSequence?): ContactSearchResult? =
        search(context, query, limit = 1).firstOrNull()

    @JvmStatic
    @JvmOverloads
    fun search(context: Context, query: CharSequence?, limit: Int = DEFAULT_LIMIT): List<ContactSearchResult> {
        if (limit <= 0 || !isExplicitContactQuery(query) || !hasContactsPermission(context)) return emptyList()
        val contactQuery = extractedQuery(query)
        testRows?.let { rows ->
            return rankRows(rows, contactQuery, limit).map {
                ContactSearchResult(it.displayName, it.phoneNumber)
            }
        }
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex < 0 || numberIndex < 0) return emptyList()
                val rows = mutableListOf<ContactRow>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val number = cursor.getString(numberIndex).orEmpty()
                    if (name.isNotBlank() && number.isNotBlank()) {
                        rows += ContactRow(name, number)
                    }
                }
                rankRows(rows, contactQuery, limit).map {
                    ContactSearchResult(it.displayName, it.phoneNumber)
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    internal fun setContactsForTesting(results: List<ContactSearchResult>?) {
        testRows = results?.map { ContactRow(it.displayName, it.phoneNumber) }
    }

    internal fun setPermissionGrantedForTesting(granted: Boolean?) {
        permissionGrantedForTesting = granted
    }

    internal fun rankRows(
        rows: List<ContactRow>,
        query: CharSequence?,
        limit: Int = DEFAULT_LIMIT
    ): List<ContactRow> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = AppSearchEngine.normalize(query)
        if (normalizedQuery.length < 2) return emptyList()
        val tokens = normalizedQuery.split("\\s+".toRegex()).filter(String::isNotEmpty)
        val queryDigits = query?.toString().orEmpty().filter(Char::isDigit)

        return rows.mapNotNull { row ->
            val normalizedName = AppSearchEngine.normalize(row.displayName)
            val phoneDigits = row.phoneNumber.filter(Char::isDigit)
            if (tokens.any { token -> !normalizedName.contains(token) } &&
                (queryDigits.length < 2 || !phoneDigits.contains(queryDigits))
            ) {
                return@mapNotNull null
            }
            val score = when {
                normalizedName == normalizedQuery -> 0
                normalizedName.startsWith(normalizedQuery) -> 10
                tokens.all { token -> normalizedName.split(" ").any { it.startsWith(token) } } -> 20
                tokens.all { token -> normalizedName.contains(token) } -> 30
                queryDigits.length >= 2 && phoneDigits.contains(queryDigits) -> 40
                else -> return@mapNotNull null
            }
            RankedContact(row, score)
        }.sortedWith(
            compareBy<RankedContact> { it.score }
                .thenBy { AppSearchEngine.normalize(it.row.displayName) }
                .thenBy { it.row.phoneNumber.filter(Char::isDigit) }
        ).take(limit).map(RankedContact::row)
    }

    private data class RankedContact(
        val row: ContactRow,
        val score: Int
    )
}
