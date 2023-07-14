package cz.feldis.sdkandroidtests.incidents

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.incidents.*
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.routeeventnotifications.IncidentInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoPolyline
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.PositionSimulator.PositionSimulatorListener
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.InOrder
import org.mockito.Mockito

class IncidentsTests : BaseTest() {

    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var incidentsManager: IncidentsManager
    private val listener: IncidentsResultListener = mock()


    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        incidentsManager = IncidentsManagerProvider.getInstance().get()
        removeAllIncidents()
    }

    private fun removeAllIncidents() {
        incidentsManager.removeAllIncidents(listener)
        verify(listener, timeout(Timeout)).onSuccess()
        verify(listener, never()).onError(any())
        reset(listener)
    }

    @Test
    fun exploreCustomIncidentsOnRouteOnlineCompute() {
        val importedSpeedCam = getMockSpeedCam()
        val importedIncidentData = IncidentData(importedSpeedCam, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()
        verify(listener, never()).onError(any())

        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.10223044006818, 17.23340438881692),
            GeoCoordinates(48.098580331935274, 17.237506607527582)
        )
        RouteExplorer.exploreIncidentsOnRoute(route, emptyList(), listener)
        val captor = argumentCaptor<List<IncidentInfo>>()
        val progressCaptor = argumentCaptor<Int>()

        do {
            verify(listener, timeout(10_000L)).onExploreIncidentsLoaded(
                captor.capture(),
                progressCaptor.capture()
            )

        } while (progressCaptor.lastValue != 100)

        var expectedSpeedcam: SpeedCamera? = null
        for (lists in captor.allValues) {
            for (incidentInfo in lists) {
                if (incidentInfo.incident is SpeedCamera) {
                    expectedSpeedcam = incidentInfo.incident as SpeedCamera
                }
            }
        }

        requireNotNull(expectedSpeedcam)
        assertEquals("ejjj_buracka", expectedSpeedcam.category)
        assertEquals("26a69832-7f72-42ba-8f1d-394811376579", expectedSpeedcam.id.uuid)
        assertEquals(1713510440, expectedSpeedcam.validToTimestamp)
        assertEquals(80, expectedSpeedcam.speedLimit)
        assertEquals(true, expectedSpeedcam.isBidirectional)
        assertEquals(359F, expectedSpeedcam.heading)
        reset(listener)
    }

    @Test
    fun exploreIncidentsOnRouteExpectEmptyExpiredIncident() {
        val importedSpeedCam = getMockSpeedCamOld()
        val importedIncidentData = IncidentData(importedSpeedCam, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.10223044006818, 17.23340438881692),
            GeoCoordinates(48.098580331935274, 17.237506607527582)
        )
        RouteExplorer.exploreIncidentsOnRoute(route, emptyList(), listener)

        verify(
            listener,
            Mockito.timeout(10_000L)
        )
            .onExploreIncidentsLoaded(argThat(List<IncidentInfo>::isEmpty), eq(100))

        verify(listener, never())
            .onExploreIncidentsError(any())

        reset(listener)
    }

    @Test
    fun onIncidentListenerTest() {
        val importedSpeedCam1 = getMockSpeedCamForAnalyzer1()
        val importedSpeedCam2 = getMockSpeedCamForAnalyzer2()
        val importedSpeedCam3 = getMockSpeedCamForAnalyzer3()
        val importedSpeedCam4 = getMockSpeedCamForAnalyzer4()
        val importedIncidentData1 = IncidentData(
            importedSpeedCam1,
            IncidentsManager.AudioNotificationParameters(-1, 0)
        )
        val importedIncidentData2 = IncidentData(importedSpeedCam2, audioNotificationParams)
        val importedIncidentData3 = IncidentData(importedSpeedCam3, audioNotificationParams)
        val importedIncidentData4 = IncidentData(importedSpeedCam4, audioNotificationParams)
        incidentsManager.addIncidents(
            listOf(
                importedIncidentData1, importedIncidentData2, importedIncidentData3, importedIncidentData4
            ), listener
        )

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.113888808253996, 17.218758652700572),
                GeoCoordinates(48.12788892758158, 17.195351511200577)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(10_000L)
        ).onIncidentsInfoChanged(argThat<List<IncidentInfo>> {
            if (this.size > 3)
                return@argThat true
            return@argThat false
        })

        simulator.stop()
        simulator.destroy()
        navigation.removeOnIncidentListener(listener)
        navigation.stopNavigation()
        reset(listener)
    }

    @Test
    fun testIncidentWithRadiusStartingOutside() {
        val importedAreaIncident = getMockRadiusIncident()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.1023, 17.2333),
                GeoCoordinates(48.098, 17.2381),
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(2F)
        simulator.start()

        verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.incident is AreaIncident)
                        if (it.distance > 0)
                            return@argThat true
                }
            }
            return@argThat false
        })

        reset(listener)
        simulator.stop()
        simulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    @Test
    fun testIncidentWithRadiusStartingInside() {
        val importedAreaIncident = getMockRadiusIncident()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.099784526591, 17.236136889475524),
                GeoCoordinates(48.098, 17.2381)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(2F)
        simulator.start()

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.distance == 0)
                        return@argThat true
                }
            }
            return@argThat false
        })

        inOrder.verify(listener, timeout(20_000L)).onIncidentsInfoChanged(emptyList())

        reset(listener)
        simulator.stop()
        simulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    @Test
    fun testIncidentWithRadiusStartingOutsideCheckThatOutside() {
        val importedAreaIncident = getMockRadiusIncident()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.1023, 17.2333),
                GeoCoordinates(48.098, 17.2381),
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(6F)
        simulator.start()

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.incident is AreaIncident)
                        if (it.distance > 0)
                            return@argThat true
                }
            }
            return@argThat false
        })

        inOrder.verify(listener, timeout(20_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.distance == 0)
                        return@argThat true
                }
            }
            return@argThat false
        })

        inOrder.verify(listener, timeout(30_000L)).onIncidentsInfoChanged(emptyList())

        reset(listener)
        simulator.stop()
        simulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    @Test
    fun testIncidentWithRadiusStartingOutsideCheckThatOutsideAfterWithoutRoute() {
        val importedAreaIncident = getMockRadiusIncidentLovosice()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)


        navigation.addOnIncidentListener(listener)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/logLovosice.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        logSimulator.start()

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.incident is AreaIncident)
                        if (it.distance > 0)
                            return@argThat true
                }
            }
            return@argThat false
        })

        inOrder.verify(listener, timeout(20_000L)).onIncidentsInfoChanged(argThat {
            if (this.isNotEmpty()) {
                this.forEach {
                    if (it.distance == 0)
                        return@argThat true
                }
            }
            return@argThat false
        })

        inOrder.verify(listener, timeout(30_000L)).onIncidentsInfoChanged(emptyList())

        reset(listener)
        logSimulator.stop()
        logSimulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    @Test
    fun testTraceIncidentOppositeDirection() {
        val importedAreaIncident = getMockPolylineIncident()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        navigation.addOnIncidentListener(listener)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/logLovosice.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        logSimulator.start()

        val positionSimulatorListener: PositionSimulatorListener = mock(verboseLogging = true)
        logSimulator.addPositionSimulatorListener(positionSimulatorListener)

        verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            this.find { it.roadPosition == GeoCoordinates(50.50351, 14.04214) } != null
        })

        reset(listener)
        logSimulator.stop()
        logSimulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    @Test
    fun testTraceIncidentInDirection() {
        val importedAreaIncident = getMockPolylineIncident()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        navigation.addOnIncidentListener(listener)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/logLovosice.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        logSimulator.start()

        verify(listener, timeout(10_000L)).onIncidentsInfoChanged(argThat {
            this.find { it.roadPosition == GeoCoordinates(50.50351, 14.04214) } != null
        })

        reset(listener)
        logSimulator.stop()
        logSimulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    // might fail if there actually is a real incident
    @Test
    fun testTraceIncidentOppositeDirectionPristavnaShouldNotNotify() {
        val importedAreaIncident = getMockPolylineIncidentPristavna()
        val importedIncidentData = IncidentData(importedAreaIncident, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        navigation.addOnIncidentListener(listener)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("pristavnaOppositeRoadTraceIncident.nmea").get()
        logSimulator.setSpeedMultiplier(4F)
        logSimulator.start()

        val positionSimulatorListener: PositionSimulatorListener = mock(verboseLogging = true)
        logSimulator.addPositionSimulatorListener(positionSimulatorListener)

        verify(positionSimulatorListener, timeout(40_000L)).onSimulatedStateChanged(eq(PositionSimulator.SimulatorState.End))

        verify(listener, never()).onIncidentsInfoChanged(argThat { isNotEmpty() })

        reset(listener)
        logSimulator.stop()
        logSimulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnIncidentListener(listener)
    }

    companion object {
        private val audioNotificationParams =
            IncidentsManager.AudioNotificationParameters(20, 25)
        private const val Timeout = 3000L
        private const val validTimestamp = 1713510440L // 19.4.2024
        private const val invalidTimestamp = 1650352040L // 19.4.2022

        private fun getMockSpeedCam(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-394811376579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                "ejjj_buracka",
                validTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamOld(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-324811371579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                "ejjj_buracka",
                invalidTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamForAnalyzer1(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-324811371579"),
                GeoCoordinates(48.11516784597829, 17.21736550170222),
                IncidentType.DangerousPlaceVehicleStopped,
                validTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamForAnalyzer2(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69834-7f72-42ba-8f1d-324811371579"),
                GeoCoordinates(48.1152235606362, 17.217302913637262),
                "tristo_hrmenych",
                validTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamForAnalyzer3(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69830-7f72-42ba-8f1d-324811371579"),
                GeoCoordinates(48.115278008993506, 17.217244118788365),
                "kategorija",
                validTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamForAnalyzer4(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69831-7f72-42ba-8f1d-324811371579"),
                GeoCoordinates(48.11534258720295, 17.21716256464312),
                IncidentType.CrashMinor,
                validTimestamp,
                359F,
                true,
                80
            )
        }

        private fun getMockRadiusIncident(): AreaIncident {
            return AreaIncident(
                IncidentId("26a69831-7f82-42ba-8f1d-324811371579"),
                GeoCoordinates(48.0994, 17.2366),
                IncidentType.CrashMinor,
                validTimestamp,
                Area.Circular(100)
            )
        }

        private fun getMockRadiusIncidentLovosice(): AreaIncident {
            return AreaIncident(
                IncidentId("26a69831-7f82-42ba-8f1d-324811371519"),
                GeoCoordinates(50.50350272545388, 14.042164306068361),
                IncidentType.CrashMinor,
                validTimestamp,
                Area.Circular(50)
            )
        }

        private fun getMockPolylineIncident(): TraceIncident {
            return TraceIncident(
                IncidentId("26a69831-7f82-42ba-8f1d-324811071519"),
                GeoCoordinates(50.50350272545388, 14.042164306068361),
                IncidentType.CrashMinor,
                validTimestamp,
                GeoPolyline(
                    listOf(
                        GeoCoordinates(50.50533588452118, 14.044448736255015),
                        GeoCoordinates(50.504295272865335, 14.043150547060886)
                    )
                )
            )
        }

        private fun getMockPolylineIncidentPristavna(): TraceIncident {
            return TraceIncident(
                IncidentId("26a69831-7f22-42ba-8f1d-324811071519"),
                GeoCoordinates(48.14222, 17.13511),
                IncidentType.DangerousPlaceCrossWind,
                validTimestamp,
                GeoPolyline(
                    listOf( // {wydHmuqgBDaB@sA?s@
                        GeoCoordinates(48.14222, 17.13511),
                        GeoCoordinates(48.1219, 17.13560),
                        GeoCoordinates(48.14218, 17.13602),
                        GeoCoordinates(48.14218, 17.13628)
                    )
                )
            )
        }
    }
}

