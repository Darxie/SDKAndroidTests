package cz.feldis.sdkandroidtests.incidents

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.incidents.*
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.routeeventnotifications.IncidentInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.navigation.OnlineNavigationTests
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Assert.assertEquals
import org.junit.Test
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
        val importedIncidentData1 = IncidentData(importedSpeedCam1, audioNotificationParams)
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

    companion object {
        private val audioNotificationParams = IncidentsManager.AudioNotificationParameters(500, 1000)

        private const val Timeout = 3000L

        private fun getMockSpeedCam(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-394811376579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                "ejjj_buracka",
                1713510440, // 19.4.2024
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
                1650352040, // 19.4.2022
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
                1713510440,
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
                1713510440,
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
                1713510440,
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
                1713510440,
                359F,
                true,
                80
            )
        }
    }
}

