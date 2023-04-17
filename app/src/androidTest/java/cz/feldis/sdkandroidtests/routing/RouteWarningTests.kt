package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.TransportMode.TransportTruck
import com.sygic.sdk.route.RoutingOptions.VehicleRestrictions
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.vehicletraits.HazmatSettings
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWarningTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()

    }

    @Test
    fun tollRoadAvoidWarningTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.07473125945471, 17.121696472685443)
        val destination = GeoCoordinates(48.08639349401805, 17.124270062341857)
        val routingOptions = RoutingOptions().apply {
            isTollRoadAvoided = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
    }

    @Test
    fun tollRoadAvoidWarningTestNegative() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.07473125945471, 17.121696472685443)
        val destination = GeoCoordinates(48.08639349401805, 17.124270062341857)

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isEmpty()
        })
    }

    @Test
    fun heightExceededTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1435, 17.19)
        val destination = GeoCoordinates(48.1505, 17.1704)
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportTruck
            addDimensionalRestriction(VehicleRestrictions.Height, 5000)
            setUseEndpointProtection(true)
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val restriction =
            captor.firstValue[0] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        assertTrue(restriction.limitValue == 4600F)
        assertTrue(restriction.realValue == 5000F)
    }

    @Test
    fun heightExceededTestPolylineCheck() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1435, 17.19)
        val destination = GeoCoordinates(48.1505, 17.1704)
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportTruck
            addDimensionalRestriction(VehicleRestrictions.Height, 5000)
            setUseEndpointProtection(true)
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()

        route.getRouteWarnings(routeWarningsListener)

        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val restriction =
            captor.firstValue[0] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        val polyline = restriction.section.geoCoordinates
        assertTrue(polyline.size > 5)
        polyline.forEach {
            assertTrue(checkFirstTwoDigits(it.latitude, "48"))
            assertTrue(checkFirstTwoDigits(it.longitude, "17"))
        }
    }

    @Test
    fun heightExceededTestNegative() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1435, 17.19)
        val destination = GeoCoordinates(48.1505, 17.1704)
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportTruck
            addDimensionalRestriction(VehicleRestrictions.Height, 4000)
            setUseEndpointProtection(true)
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isEmpty()
        })
    }

    @Test
    fun hazmatAndTunneViolationTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1586, 17.0763)
        val destination = GeoCoordinates(48.1661, 17.0698)
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportTruck
            this.hazmatSettings = HazmatSettings( // sitina has genhazmat and tunnel E
                isGeneralHazardousMaterial = true,
                isExplosiveMaterial = false,
                isGoodsHarmfulToWater = false
            ).apply {
                this.tunnelCategory = HazmatSettings.HazmatTunnelCategory.E
            }
            setUseEndpointProtection(true)
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedHazmatRestriction } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedTunnelRestriction } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedEmissionStandard } == null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val restriction = captor.firstValue.find {
            it is RouteWarning.SectionWarning.ZoneViolation.ViolatedTunnelRestriction
        }
        restriction?.let {
            val tun = it as RouteWarning.SectionWarning.ZoneViolation.ViolatedTunnelRestriction
            assertTrue(tun.limitValue == HazmatSettings.HazmatTunnelCategory.E)
            assertTrue(tun.realValue == HazmatSettings.HazmatTunnelCategory.E)
        }
    }
}

fun checkFirstTwoDigits(num: Double, expected: String): Boolean {
    val strNum = num.toString()
    return strNum.length >= 2 && strNum.substring(0, 2) == expected
}