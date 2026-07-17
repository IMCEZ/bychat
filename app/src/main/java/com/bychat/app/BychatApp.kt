package com.bychat.app

import android.app.Application

class BychatApp : Application() {
    lateinit var db: LocalDb
        private set

    override fun onCreate() {
        super.onCreate()
        db = LocalDb(this)
    }
}
