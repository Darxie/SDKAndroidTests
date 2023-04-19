package cz.feldis.sdkandroidtests.incidents

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.incidents.*
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.routeeventnotifications.IncidentInfo
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class IncidentsTests : BaseTest(){

    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var incidentsManager: IncidentsManager
    private val listener: IncidentsResultListener = mock()


    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        incidentsManager = IncidentsManagerProvider.getInstance().get()
        removeAllIncidents()
    }

    @Test
    fun removeAllIncidents() {
        incidentsManager.removeAllIncidents(listener)
        verify(listener, timeout(Timeout)).onSuccess()
        verify(listener, never()).onError(any())
        reset(listener)
    }

    @Test
    fun exploreIncidentsOnRoute() {
        val importedSpeedCam = getMockSpeedCam()
        val importedIncidentData = IncidentData(importedSpeedCam, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(Timeout)).onSuccess()

        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.10223044006818, 17.23340438881692),
            GeoCoordinates(48.098580331935274, 17.237506607527582)
        )
        RouteExplorer.exploreIncidentsOnRoute(route, emptyList(), listener)
        val captor = argumentCaptor<List<IncidentInfo>>()

        verify(
            listener,
            Mockito.timeout(30_000L)
        )
            .onExploreIncidentsLoaded(captor.capture(), eq(100))

        verify(listener, never())
            .onExploreIncidentsError(any())

        val expectedSpeedcam = captor.firstValue[0].incident as SpeedCamera
        assertEquals("ejjj_buracka", expectedSpeedcam.category)
        assertEquals("26a69832-7f72-42ba-8f1d-394811376579", expectedSpeedcam.id.uuid)
        assertEquals(1713510440, expectedSpeedcam.validToTimestamp)
        assertEquals(80, expectedSpeedcam.speedLimit)
        assertEquals(true, expectedSpeedcam.isBidirectional)
        assertEquals(359F, expectedSpeedcam.heading)
        reset(listener)
    }

    @Test
    fun exploreIncidentsOnRouteExpectEmpty() {
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

    companion object {
        private val audioNotificationParams = IncidentsManager.AudioNotificationParameters(5, 10)
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
    }
}

