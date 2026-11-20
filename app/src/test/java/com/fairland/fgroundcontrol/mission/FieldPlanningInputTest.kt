package com.fairland.fgroundcontrol.mission
import org.junit.Assert.*
import org.junit.Test
class FieldPlanningInputTest {
    @Test fun parameterEndpointsAndInvalidValues() {
        FieldPlanningParameters(1.0,1.0,1.5);FieldPlanningParameters(120.0,12.0,70.0)
        assertThrows(IllegalArgumentException::class.java) { FieldPlanningParameters(Double.NaN,5.0,5.0) }
        assertThrows(IllegalArgumentException::class.java) { FieldPlanningParameters(4.0,12.1,5.0) }
        assertThrows(IllegalArgumentException::class.java) { FieldPlanningParameters(4.0,5.0,1.4) }
    }
    private fun p(x: Double,y: Double)=BoundaryPoint(y/111319.49079327358,x/111319.49079327358)
    @Test fun rectangleProjectsInMetersAndAcceptsEitherWinding() {
        val points=listOf(p(0.0,0.0),p(100.0,0.0),p(100.0,60.0),p(0.0,60.0))
        val projected=FieldBoundaryValidation.projectAndValidate(points)
        assertEquals(100.0,projected[1].east,0.001);assertEquals(60.0,projected[2].north,0.001)
        assertEquals(4,FieldBoundaryValidation.projectAndValidate(points.reversed()).size)
    }
    @Test fun badGeometryIsRejected() {
        for(points in listOf(emptyList(),listOf(p(0.0,0.0),p(1.0,0.0),p(2.0,0.0)),listOf(p(0.0,0.0),p(10.0,10.0),p(0.0,10.0),p(10.0,0.0)),listOf(p(0.0,0.0),p(0.01,0.0),p(10.0,10.0))))
            assertThrows(IllegalArgumentException::class.java) { FieldBoundaryValidation.projectAndValidate(points) }
    }
}
