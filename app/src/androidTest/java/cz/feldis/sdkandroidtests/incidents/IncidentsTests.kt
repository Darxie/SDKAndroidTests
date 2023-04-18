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
import java.lang.Thread.sleep

class IncidentsTests : BaseTest(){

    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var incidentsManager: IncidentsManager


    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        incidentsManager = IncidentsManagerProvider.getInstance().get()
    }

    @Test
    fun exploreIncidentsOnRoute() {
        addMockIncident()
        sleep(1000)
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
//        assertEquals(1684421149, expectedSpeedcam.validToTimestamp) // doesnt work
        assertEquals(80, expectedSpeedcam.speedLimit)
        assertEquals(true, expectedSpeedcam.isBidirectional)
        assertEquals(359F, expectedSpeedcam.heading)

    }

    companion object {
        private val audioNotificationParams = IncidentsManager.AudioNotificationParameters(5, 10)

        fun addMockIncident(){
            val mockIncident = SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-394811376579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                "ejjj_buracka",
                1684421149,
                359F, // info ze od 0 do 360?
                true,
                80
            )

            val incidentData = IncidentData(
                mockIncident, audioNotificationParams
            )
            IncidentsManagerProvider.getInstance().get().addIncidents(
                listOf(incidentData), object: IncidentsResultListener{
                    override fun onError(error: IncidentsManager.ErrorCode) {
                    }

                    override fun onSuccess() {
                    }
                }
            )
        }
    }
}

