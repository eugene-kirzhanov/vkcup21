package by.anegin.vkcup21.features.taxi.tools.impl.mapbox

import android.content.Context
import by.anegin.vkcup21.di.IoDispatcher
import by.anegin.vkcup21.features.taxi.models.Position
import by.anegin.vkcup21.features.taxi.models.Route
import by.anegin.vkcup21.features.taxi.tools.RouteBuilder
import by.anegin.vkcup21.taxi.R
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MapboxRouteBuilder @Inject constructor(
    private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : RouteBuilder {

    companion object {
        private const val MINIMUM_VALID_ROUTE_DISTANCE = 100 // in meters
    }

    override suspend fun buildRoute(sourceLatLng: Position?, destinationLatLng: Position?): Route? = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            if (sourceLatLng == null || destinationLatLng == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val sourcePoint = Point.fromLngLat(sourceLatLng.longitude, sourceLatLng.latitude)
            val destPoint = Point.fromLngLat(destinationLatLng.longitude, destinationLatLng.latitude)

            val distance = TurfMeasurement.distance(sourcePoint, destPoint, TurfConstants.UNIT_METERS)
            if (distance < MINIMUM_VALID_ROUTE_DISTANCE) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val client = MapboxDirections.builder()
                .origin(sourcePoint)
                .destination(destPoint)
                .overview(DirectionsCriteria.OVERVIEW_FULL)
                .alternatives(true)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .accessToken(context.getString(R.string.mapbox_access_token))
                .build()

            continuation.invokeOnCancellation {
                client.cancelCall()
            }

            try {
                client.enqueueCall(object : Callback<DirectionsResponse> {
                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        response.body()?.let { body ->
                            val route = body.routes().firstOrNull()?.let {
                                Route(
                                    latitude = destinationLatLng.latitude,
                                    longitude = destinationLatLng.longitude,
                                    direction = it
                                )
                            }
                            continuation.resume(route)
                        } ?: run {
                            continuation.resumeWithException(IOException("Empty response body"))
                        }
                    }

                    override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                        continuation.resumeWithException(t)
                    }
                })
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
            }
        }
    }

}