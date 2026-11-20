package com.fairland.fgroundcontrol.mission

import org.junit.Assert.*
import org.junit.Test

class TaskMapSelectionTest {
    @Test fun endpointArrowsFollowTravelDirectionAndIgnoreDuplicates() {
        val p=BoundaryPoint(0.0,0.0)
        fun rotation(q: BoundaryPoint)=routeEndpointRotation(listOf(p,p,q),true)!!
        assertEquals(0.0,rotation(BoundaryPoint(0.0,1.0)),1e-9)
        assertEquals(270.0,rotation(BoundaryPoint(1.0,0.0)),1e-9)
        assertEquals(90.0,rotation(BoundaryPoint(-1.0,0.0)),1e-9)
        assertEquals(180.0,rotation(BoundaryPoint(0.0,-1.0)),1e-9)
        assertEquals(180.0,routeEndpointRotation(listOf(p,BoundaryPoint(0.0,1.0),p,p),false)!!,1e-9)
        assertNull(routeEndpointRotation(listOf(p,p),true))
    }
    @Test fun labelUsesAreaCenterAndStaysInsideConcaveField() {
        val triangle=listOf(BoundaryPoint(0.0,0.0),BoundaryPoint(0.0,3.0),BoundaryPoint(3.0,0.0))
        val center=taskLabelCenter(triangle)
        assertEquals(1.0,center.first,1e-9);assertEquals(1.0,center.second,1e-9)
        val concave=listOf(BoundaryPoint(0.0,0.0),BoundaryPoint(0.0,3.0),BoundaryPoint(3.0,3.0),BoundaryPoint(3.0,2.0),BoundaryPoint(1.0,2.0),BoundaryPoint(1.0,1.0),BoundaryPoint(3.0,1.0),BoundaryPoint(3.0,0.0))
        val inner=taskLabelCenter(concave)
        assertTrue(containsTaskPoint(concave,inner.first,inner.second))
        val wrapped=triangle.map {it.copy(longitude=if(it.longitude==0.0) 179.9 else -179.9)}
        val date=taskLabelCenter(wrapped)
        assertTrue(containsTaskPoint(wrapped,date.first,date.second))
    }
    @Test fun interiorEdgeOutsideAndDateLine() {
        val square=listOf(BoundaryPoint(0.0,0.0),BoundaryPoint(0.0,1.0),BoundaryPoint(1.0,1.0),BoundaryPoint(1.0,0.0))
        assertTrue(containsTaskPoint(square,0.5,0.5))
        assertTrue(containsTaskPoint(square,0.0,0.5))
        assertFalse(containsTaskPoint(square,2.0,0.5))
        assertFalse(containsTaskPoint(square.take(2),0.0,0.5))
        val wrapped=listOf(BoundaryPoint(0.0,179.9),BoundaryPoint(0.0,-179.9),BoundaryPoint(1.0,-179.9),BoundaryPoint(1.0,179.9))
        assertTrue(containsTaskPoint(wrapped,0.5,180.0))
        assertFalse(containsTaskPoint(wrapped,0.5,0.0))
    }
}
