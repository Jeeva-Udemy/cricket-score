package com.example.cricketscorer

import android.app.Application
import com.example.cricketscorer.data.CricketDatabase
import com.example.cricketscorer.data.CricketRepository

class CricketApplication : Application() {

    val database by lazy { CricketDatabase.getInstance(this) }
    val repository by lazy { CricketRepository(database.cricketDao()) }
}
