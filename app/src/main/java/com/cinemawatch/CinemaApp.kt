package com.cinemawatch

import android.app.Application
import com.cinemawatch.data.CinemaDatabase
import com.cinemawatch.data.CinemaRepository
import com.cinemawatch.radio.ScanEngine

class CinemaApp : Application() {
    val repository by lazy { CinemaRepository(CinemaDatabase.create(this)) }
    val scanner by lazy { ScanEngine(this, repository) }
}
