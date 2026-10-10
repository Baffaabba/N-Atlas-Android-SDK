package io.github.baffaabba.natlas.internal

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.util.UUID

/**
 * A random id created once per install and kept in SharedPreferences. It is sent as
 * `X-NAtlas-User` so the server can count active users for the licence cap. It is not
 * derived from any device identifier.
 */
internal object InstallId {
    private const val PREFS = "io.github.baffaabba.natlas"
    private const val KEY = "install_id"

    /** Used when no Context is available (plain JVM, or the provider was removed). */
    private val processFallback: String by lazy { UUID.randomUUID().toString() }

    fun get(context: Context?): String {
        val ctx = context ?: return processFallback
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            prefs.getString(KEY, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY, id).apply()
            return id
        }
    }
}

/** Holds the application context captured by [NAtlasInitProvider]. */
internal object AppContext {
    @SuppressLint("StaticFieldLeak") // Application context only; it lives as long as the process.
    @Volatile
    var value: Context? = null
}

/**
 * Captures the application context at app start so `NAtlasClient(config)` needs no Context.
 * Declared in the library manifest; it does nothing else.
 */
internal class NAtlasInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        AppContext.value = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
