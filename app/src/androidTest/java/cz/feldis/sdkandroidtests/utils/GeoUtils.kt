package cz.feldis.sdkandroidtests.utils

import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates

object GeoUtils {

    fun isPointInBoundingBox(point: GeoCoordinates, boundingBox: GeoBoundingBox): Boolean {
        val withinLatitude = point.latitude <= boundingBox.topLeft.latitude &&
                point.latitude >= boundingBox.bottomRight.latitude
        val withinLongitude = point.longitude >= boundingBox.topLeft.longitude &&
                point.longitude <= boundingBox.bottomRight.longitude
        return withinLatitude && withinLongitude
    }
}