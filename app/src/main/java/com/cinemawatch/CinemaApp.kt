package com.cinemawatch

import android.app.Application
import com.cinemawatch.data.CinemaDatabase
import com.cinemawatch.data.CinemaRepository
import com.cinemawatch.radio.ScanEngine

class CinemaApp : Application() {
    internal var updateSource: com.cinemawatch.update.UpdateSource = com.cinemawatch.update.GithubUpdates(this)
    val updates by lazy { com.cinemawatch.update.UpdateController { updateSource } }
    val repository by lazy { CinemaRepository(CinemaDatabase.create(this)) }
    val scanner by lazy { ScanEngine(this, repository) }
}
