package com.fairland.fgroundcontrol.mission

import org.junit.Assert.*
import org.junit.Test

class TaskOrderingTest {
    @Test fun distanceHandlesCoincidenceMissingAndAntimeridian() {
        val origin=BoundaryPoint(0.0,0.0)
        assertEquals(0.0,TaskOrdering.distance(origin,origin),0.0)
        assertEquals(Double.POSITIVE_INFINITY,TaskOrdering.distance(origin,null),0.0)
        assertEquals(111319.49,TaskOrdering.distance(origin,BoundaryPoint(0.0,1.0)),0.1)
        assertEquals(22263.898,TaskOrdering.distance(BoundaryPoint(0.0,179.9),BoundaryPoint(0.0,-179.9)),0.1)
        assertTrue(TaskOrdering.distance(origin,BoundaryPoint(0.0,1.0))<TaskOrdering.distance(origin,BoundaryPoint(0.0,2.0)))
    }
}
