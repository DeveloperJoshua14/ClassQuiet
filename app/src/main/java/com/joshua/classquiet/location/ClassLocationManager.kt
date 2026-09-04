package com.joshua.classquiet.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.joshua.classquiet.model.LocationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val accuracyMeters: Float? = null,
)

class ClassLocationManager(private val context: Context) {
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(): LocationSnapshot? {
        if (!hasFineLocation()) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(12_000L)
            .setMaxUpdateAgeMillis(2 * 60 * 1000L)
            .build()
        val fresh = runCatching { awaitCurrentLocation(request) }.getOrNull()
        if (fresh != null) return fresh.toSnapshot()

        val last = runCatching { awaitLastLocation() }.getOrNull() ?: return null
        return last.takeIf {
            System.currentTimeMillis() - it.time <= FALLBACK_MAX_AGE_MILLIS
        }?.toSnapshot()
    }

    suspend fun currentResolvedLocation(): ResolvedLocation? {
        val snapshot = currentLocation() ?: return null
        val address = reverseGeocode(snapshot.latitude, snapshot.longitude)
        return ResolvedLocation(
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            address = address.orEmpty(),
            accuracyMeters = snapshot.accuracyMeters,
        )
    }

    suspend fun resolveAddress(query: String): ResolvedLocation? {
        if (!Geocoder.isPresent() || query.isBlank()) return null
        val address = firstAddressForName(query.trim()) ?: return null
        return ResolvedLocation(
            latitude = address.latitude,
            longitude = address.longitude,
            address = address.getAddressLine(0).orEmpty(),
        )
    }

    @Suppress("MissingPermission")
    private suspend fun awaitCurrentLocation(request: CurrentLocationRequest): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            fusedLocation.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
        }

    @Suppress("MissingPermission")
    private suspend fun awaitLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocation.lastLocation
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
        }

    private suspend fun firstAddressForName(query: String): Address? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, 1)?.firstOrNull() }
        }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
        return address?.getAddressLine(0)
    }

    private fun Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else 0f,
        capturedAtMillis = time,
    )

    private companion object {
        const val FALLBACK_MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
