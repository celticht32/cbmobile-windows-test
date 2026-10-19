package com.couchbase.inspectionsapp

import android.app.Application
import android.util.Log
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.DatabaseManager
import com.couchbase.lite.CouchbaseLite
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.LogLevel
import com.couchbase.lite.logging.ConsoleLogSink
import com.couchbase.lite.logging.LogSinks

class InspectionsAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run once before ANY other Couchbase Lite call.
        CouchbaseLite.init(this)
        // Verbose console logging in debug: filter Logcat by "CouchbaseLite".
        if (BuildConfig.DEBUG) {
            LogSinks.get().setConsole(ConsoleLogSink(LogLevel.VERBOSE))
        }
        AuthState.init(this)
        // Local-first: the database is opened at launch so data on disk is available immediately.
        try {
            DatabaseManager.open()
        } catch (e: CouchbaseLiteException) {
            Log.e("InspectionsApp", "Failed to open database", e)
            DatabaseManager.reportOpenError(e)
        }
    }
}
