package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.navigation.NavigationManager.OnVehicleZoneListener
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.hazmat.HazmatTraits
import com.sygic.sdk.vehicletraits.hazmat.TunnelCategory
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import kotlin.math.abs

class RouteWarningTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    private val navigationManagerKtx = NavigationManagerKtx()

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
        disableOnlineMaps()
    }

    @Test
    fun tollRoadAvoidWarningTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.22920490622517, 17.35421334454338)
        val destination = GeoCoordinates(48.243843523144996, 17.356337654048843)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
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
    fun tollRoadAvoidWarningTwoCountriesTest() {
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.069028686812096, 17.08606355787871)
        val destination = GeoCoordinates(48.082140106027985, 17.03615405945805)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()
        route.getRouteWarnings(routeWarningsListener)

        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())
        val restrictions = captor.allValues.flatten()
        val restriction1 =
            restrictions[0] as RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad
        val restriction2 =
            restrictions[1] as RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad
        assertTrue(restriction1.iso == "sk")
        assertTrue(restriction2.iso == "at")
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
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 5000
                }
            }
            useEndpointProtection = true
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
        assertTrue(restriction.iso == "sk")
    }

    @Test
    fun heightExceededTestPolylineCheck() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1435, 17.19)
        val destination = GeoCoordinates(48.1505, 17.1704)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 5000
                }
            }
            useEndpointProtection = true
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
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 4000
                }
            }
            useEndpointProtection = true
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
    fun hazmatAndTunnelViolationTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1586, 17.0763)
        val destination = GeoCoordinates(48.1661, 17.0698)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                hazmatTraits =
                    HazmatTraits(HazmatTraits.GeneralHazardousMaterialClasses, TunnelCategory.E)
            }
            useEndpointProtection = true
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
            assertTrue(tun.limitValue == TunnelCategory.E)
            assertTrue(tun.realValue == TunnelCategory.E)
        }
    }

    @Test
    fun startAndEndInViolationCheckValues() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1422, 17.1271)
        val destination = GeoCoordinates(48.142, 17.1278)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 5000
                }
            }
            useEndpointProtection = true
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

        val restriction1 =
            captor.firstValue[0] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        val restriction2 =
            captor.firstValue[1] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        assertTrue(restriction1.limitValue == 1900F)
        assertTrue(restriction1.realValue == 5000F)
        assertTrue(restriction2.limitValue == 2100F)
        assertTrue(restriction2.realValue == 5000F)
    }

    @Test
    fun startAndEndInViolationCheckValues2() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1652, 17.162)
        val destination = GeoCoordinates(48.1598, 17.1806)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 5000
                }
            }
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
            arriveInDrivingSide = false
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

        val restriction1 =
            captor.firstValue[0] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        val restriction2 =
            captor.firstValue[1] as RouteWarning.SectionWarning.DimensionalRestriction.ExceededHeight
        assertTrue(restriction1.limitValue == 3000F)
        assertTrue(restriction1.realValue == 5000F)
        assertTrue(restriction2.limitValue == 4500F)
        assertTrue(restriction2.realValue == 5000F)
    }

    @Test
    @Ignore("ToDo - will work on v3 soon")
    fun tollRoadAvoidWarningTestOnline() {
        enableOnlineMaps()
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.07473125945471, 17.121696472685443)
        val destination = GeoCoordinates(48.41623783484128, 17.747376207492863)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
        }

        val route = routeComputeHelper.onlineComputeRoute(
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
    fun tollRoadCountryAvoidWarningTestOnline() {
        enableOnlineMaps()
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1083, 17.2206)
        val destination = GeoCoordinates(51.9035, -0.47722)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.countryRouteAvoids =
                mutableMapOf("gb" to mutableSetOf(RouteAvoids.Type.Highway))
        }

        val captor = argumentCaptor<List<RouteWarning>>()
        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.CountryAvoidViolation.UnavoidableHighway } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        val restriction = captor.allValues.flatten()
            .first() as RouteWarning.SectionWarning.CountryAvoidViolation.UnavoidableHighway
        assertTrue(restriction.iso == "gb")
    }

    /**
     * https://jira.sygic.com/browse/SDC-10869
     *
     * We want to avoid country "sk" but make a route in that country.
     * We then check that there is an UnavoidableCountry warning that contains the iso.
     */
    @Test
    fun unavoidableCountryWarningContainsIso() {
        mapDownloadHelper.installAndLoadMap("sk")
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)
        val start = GeoCoordinates(48.11964833044328, 17.211256171240564)
        val destination = GeoCoordinates(48.12286230190469, 17.201664587844974)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.countryRouteAvoids =
                mutableMapOf("sk" to mutableSetOf(RouteAvoids.Type.Country))
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()
        route.getRouteWarnings(routeWarningsListener)

        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val iso =
            (captor.firstValue[0] as RouteWarning.SectionWarning.CountryAvoidViolation.UnavoidableCountry).iso

        assertEquals("sk", iso)
    }

    @Test
    fun endInEmissionZoneTest() {
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.069192, 17.087216)
        val destination = GeoCoordinates(48.081228, 17.043694)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                powertrainTraits = PowertrainTraits.InternalCombustionPowertrain(
                    FuelType.Diesel, EuropeanEmissionStandard.Euro1, ConsumptionData()
                )
                dimensionalTraits = DimensionalTraits().apply {
                    totalHeight = 5000
                }
            }
            useEndpointProtection = true
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
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedEmissionStandard } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val restriction1 =
            captor.firstValue[0] as RouteWarning.SectionWarning.ZoneViolation.ViolatedEmissionStandard
        assert(restriction1.limitValue == EuropeanEmissionStandard.Euro2)
        assertTrue(restriction1.iso == "at")
    }

    @Test
    fun possiblyUnsuitableUnpavedRoadWarningTest() {
        mapDownloadHelper.installAndLoadMap("is")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(63.568, -19.8009)
        val destination = GeoCoordinates(63.57023, -19.79725)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.UnpavedRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()
        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableUnpavedRoad } != null
                    && this.find { it is RouteWarning.SectionWarning.PossiblyUnsuitableSection.UnpavedSection } != null
        })
        val restriction1 =
            captor.firstValue[0] as RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableUnpavedRoad
        val restriction2 =
            captor.firstValue[1] as RouteWarning.SectionWarning.PossiblyUnsuitableSection.UnpavedSection
        assertTrue(restriction1.iso == "is")
        assertTrue(restriction2.iso == "is")
    }

    @Test
    fun ipmBlockWarningTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.22710485629786, 17.001576996478246)
        val destination = GeoCoordinates(48.236941358263444, 16.98775721434121)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.UnpavedRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedProhibitedZone } != null
        })
    }

    @Test
    fun ipmBlockWarningTestNegative() {
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.22710485629786, 17.001576996478246)
        val destination = GeoCoordinates(48.22724113173217, 16.997684137165376)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.UnpavedRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.ZoneViolation.ViolatedProhibitedZone } == null
        })
    }

    /**
     * https://jira.sygic.com/browse/SDC-10811
     *
     * In this test a route is computed through a tunnel and it is expected
     * that vehicle zone restriction information is passed through the NavigationManager.
     */
    @Test
    fun testVehicleZonesBranisko() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance()

        val setVehicleProfileListener: SetVehicleProfileListener = mock(verboseLogging = true)
        val vehicleZoneListener: OnVehicleZoneListener = mock(verboseLogging = true)

        val vehProf = routeComputeHelper.createCombustionVehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Truck
            hazmatTraits = HazmatTraits(emptySet(), TunnelCategory.E)
        }
        navigation
            .setVehicleProfile(vehProf, setVehicleProfileListener)

        val start = GeoCoordinates(49.0093, 20.8322)
        val destination = GeoCoordinates(49.0086, 20.8657)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = vehProf
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        assertNotNull(route)
        navigationManagerKtx.setRouteForNavigation(route, navigation)

        navigation.addOnVehicleZoneListener(vehicleZoneListener)
        verify(vehicleZoneListener, timeout(10_000L)).onVehicleZoneInfo(argThat {
            this.find { it.restriction.type == RestrictionInfo.RestrictionType.CargoTunnel } != null
        })
    }

    @Test
    fun testSpecialTollRoadWarning() = runBlocking {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1655149641659, 17.151219976297632)
        val destination = GeoCoordinates(48.376850, 17.599600)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
            vehicleProfile = VehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
            }
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
    }

    @Test
    fun testSpecialTollRoadWarningCarNegative() = runBlocking {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1655149641659, 17.151219976297632)
        val destination = GeoCoordinates(48.376850, 17.599600)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } == null
        })
    }

    /**
     * https://jira.sygic.com/browse/SDC-14368
     */
    @Test
    fun coordinatesOfInsufficientBatteryWarning() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.13435749214434, 17.139510367591342)
        val destination = GeoCoordinates(48.31550136420124, 18.050290453922088)

        val options = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createEVProfileForInsufficientBattery(2f, 2f)
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val captorWarnings = argumentCaptor<List<RouteWarning>>()
        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = options
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(10_000L)).onRouteWarnings(captorWarnings.capture())

        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } == null
        })

        val warnings = captorWarnings.allValues.flatten()
        val tolerance = 10e-5

        val batteryWarnings =
            warnings.filterIsInstance<RouteWarning.LocationWarning.InsufficientBatteryCharge>()
        assertTrue(
            "Expected at least one InsufficientBatteryCharge warning",
            batteryWarnings.isNotEmpty()
        )

        for (warning in batteryWarnings) {
            val warningLocation = warning.location
            val isAtDestination =
                abs(warningLocation.latitude - destination.latitude) < tolerance &&
                        abs(warningLocation.longitude - destination.longitude) < tolerance

            assertFalse(
                "InsufficientBatteryCharge warning should not be at the destination",
                isAtDestination
            )
        }
    }


    @Test
    fun preferenceViolationWarningEVTest() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.14548507020328, 17.126529723864405)
        val destination = GeoCoordinates(48.217657544377715, 17.406051728312903)
        val options = RoutingOptions().apply {
            vehicleProfile =
                routeComputeHelper.createElectricVehicleProfileForPreferenceViolation(50f, 5f)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = options
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.LocationWarning.EVPreferenceViolation } != null
        })
    }
}

fun checkFirstTwoDigits(num: Double, expected: String): Boolean {
    val strNum = num.toString()
    return strNum.length >= 2 && strNum.substring(0, 2) == expected
}