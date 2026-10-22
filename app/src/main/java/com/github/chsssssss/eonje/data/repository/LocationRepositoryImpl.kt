package com.github.chsssssss.eonje.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.model.LocationAvailability
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val locationManager by lazy { context.getSystemService(LocationManager::class.java) }

    // hasLocationPermission()에서 이미 확인하는데도 lint가 별도 함수로 분리된 권한 체크는 못 따라가서 뜨는 오탐이다.
    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return try {
            // lastLocation은 즉시 돌아오지만 한 번도 측위한 적 없는 기기에서는 null이라 그때만 새로 잡는다.
            val location = client.lastLocation.await()
                ?: client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
            location?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun checkAvailability(): LocationAvailability = when {
        !hasLocationPermission() -> LocationAvailability.PERMISSION_DENIED
        !LocationManagerCompat.isLocationEnabled(locationManager) -> LocationAvailability.LOCATION_OFF
        else -> LocationAvailability.AVAILABLE
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
