package com.cinemawatch

import org.junit.Assert.*
import org.junit.Test

class PlanGeometryTest {
    @Test fun legacyRectanglesBecomeRoomOutlinesWithoutLosingLabels() {
        val pin = PlanPin("zone:a", .5f, .5f, .4f, .2f)
        val points = PlanGeometry.outline(pin)
        assertEquals(4, points.size); assertEquals(.08f, PlanGeometry.area(points), .0001f)
        assertTrue(PlanGeometry.contains(points, PlanPoint(.5f, .5f)))
        assertFalse(PlanGeometry.contains(points, PlanPoint(.9f, .9f)))
    }
    @Test fun nonRectangularCorridorsRemainEditableAndCannotBeMovedOutOfBounds() {
        val points = listOf(PlanPoint(.1f,.1f), PlanPoint(.8f,.1f), PlanPoint(.8f,.3f), PlanPoint(.3f,.3f), PlanPoint(.3f,.8f), PlanPoint(.1f,.8f))
        val corridor = PlanGeometry.shape("zone:c", points)
        assertTrue(PlanGeometry.contains(points, PlanPoint(.2f,.7f)))
        assertFalse(PlanGeometry.contains(points, PlanPoint(.7f,.7f)))
        val moved = PlanGeometry.move(corridor, 2f, -2f)
        assertTrue(moved.vertices.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(PlanGeometry.area(points), PlanGeometry.area(moved.vertices), .0001f)
    }
    @Test fun gridAndRightAngleSnapCanBeDisabled() {
        assertEquals(PlanPoint(.4f,.2f), PlanGeometry.snap(PlanPoint(.409f,.29f), PlanPoint(.2f,.2f), true))
        assertEquals(PlanPoint(.409f,.29f), PlanGeometry.snap(PlanPoint(.409f,.29f), PlanPoint(.2f,.2f), false))
    }
}
