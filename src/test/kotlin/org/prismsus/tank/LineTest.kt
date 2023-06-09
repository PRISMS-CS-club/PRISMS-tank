package org.prismsus.tank

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.prismsus.tank.utils.*
import org.prismsus.tank.utils.collidable.ColRect
import org.prismsus.tank.utils.collidable.DPos2
import org.prismsus.tank.utils.collidable.Line
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LineTest {

    @Test
    fun intersect() {
        // test if two same lines can intersect
        var line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        Assertions.assertTrue(line1.inter == 0.0)
        Assertions.assertTrue(line1.slope == 1.0)
        var line2 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        Assertions.assertTrue(line1 collide line2)
        Assertions.assertTrue(line2.collide(line1))
        // test the case where two lines are parallel, but not intersect within their range
        line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        line2 = Line(DPos2(0.0, 1.0), DPos2(1.0, 2.0))
        Assertions.assertFalse(line1.collide(line2))
        Assertions.assertFalse(line2.collide(line1))
        // test the case where there are two lines with different slope, and should intersect
        line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        line2 = Line(DPos2(0.0, 1.0), DPos2(1.0, 0.0))
        Assertions.assertTrue(line1.collide(line2))
        Assertions.assertTrue(line2.collide(line1))
        // test the case where there are two lines with different slope
        // and will intersect, but outside the endpoints
        line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        line2 = Line(DPos2(0.0, 2.0), DPos2(1.0, 1.1))
        Assertions.assertFalse(line1.collide(line2))
        Assertions.assertFalse(line2.collide(line1))
        // case where the line are touching
        line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
        line2 = Line(DPos2(0.0, 2.0), DPos2(1.0, 1.0))
        Assertions.assertTrue(line2.inter == 2.0)
        Assertions.assertTrue(line2.slope == -1.0)
        Assertions.assertTrue(line1.inter == 0.0)
        Assertions.assertTrue(line1.slope == 1.0)
        Assertions.assertTrue(line1.collide(line2))
        Assertions.assertTrue(line2.collide(line1))
        // cases when the lines are vertical
        line1 = Line(DPos2.ORIGIN, DPos2.UP)
        line2 = Line((DVec2.ORIGIN + DVec2.LF * .1).toPt(), DPos2.RT + DVec2.UP * .1)
        Assertions.assertTrue(line1.collide(line2))
        Assertions.assertTrue(line2.collide(line1))
    }

    @Test
    fun intersectPts(){
        run{
            // create a horizontal line and a vertical line
            val line1 = Line(DPos2.ORIGIN, DPos2(1.0, 0.0))
            val line2 = Line(DPos2.ORIGIN, DPos2(0.0, 1.0))
            // test if the intersection point is correct
            Assertions.assertTrue(line1.collidePts(line2).size == 1)
            Assertions.assertEquals(line1.collidePts(line2)[0], DPos2.ORIGIN)

            line2 -= DVec2(0.0, .5)
            Assertions.assertTrue(line1.collidePts(line2).size == 1)
            Assertions.assertEquals(line1.collidePts(line2)[0], DPos2(0.0, 0.0))

            line2 += DVec2(.5, .0)
            Assertions.assertTrue(line1.collidePts(line2).size == 1)
            Assertions.assertEquals(line1.collidePts(line2)[0], DPos2(.5, 0.0))
        }

        run{
            // test the case when two lines are parallel
            val line1 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
            val line2 = Line(DPos2.ORIGIN, DPos2(1.0, 1.0))
            Assertions.assertEquals(line1.collidePts(line2).size, 2)
            Assertions.assertTrue(line1.collidePts(line2).contentEquals(arrayOf(DPos2.ORIGIN, DPos2(1.0, 1.0))))

            line2 -= DVec2(.5, .5)
            Assertions.assertEquals(line1.collidePts(line2).size, 2)
            Assertions.assertTrue(line1.collidePts(line2).contentEquals(arrayOf(DPos2.ORIGIN, DPos2(.5, .5))))
        }

        run{
            // two lines forms a cross at the center
            val line1 = Line(DPos2(-.5, -.5), DPos2(.5, .5))
            val line2 = Line(DPos2(-.5, .5), DPos2(.5, -.5))
            Assertions.assertEquals(line1.collidePts(line2).size, 1)
            Assertions.assertEquals(line1.collidePts(line2)[0], DPos2.ORIGIN)
        }
    }

    @Test
    fun rotate() {
        // rotate a line by 90 degree, from flat line to vertical
        var line = Line(DPos2.ORIGIN, DPos2(1.0, 0.0))
        var lineRotated = Line(DPos2.ORIGIN, DPos2(0.0, 1.0))
        Assertions.assertTrue(line.rotateDeg(90.0, DPos2.ORIGIN) == lineRotated)

        // rotate a line by -90 degree, from flat to vertical down
        lineRotated = Line(DPos2.ORIGIN, DPos2(0.0, -1.0))
        Assertions.assertTrue(line.rotateDeg(-90.0, DPos2.ORIGIN) == lineRotated)

        // rotate a line by 90 degree, but centered at the middle of the line (0.5, 0.0)
        lineRotated = Line(DPos2(0.5, -0.5), DPos2(0.5, 0.5))
        Assertions.assertTrue(line.rotateDeg(90.0, DPos2(0.5, 0.0)) == lineRotated)

        // rotate a line by a randomly generated angle
        val rad = Math.random() * PI / 2
        lineRotated = Line(DPos2.ORIGIN, DPos2(cos(rad), sin(rad)))
        Assertions.assertTrue(line.rotate(rad, DPos2.ORIGIN) == lineRotated)
    }


    @Test
    fun angleRotated(){
        // rotate the line from the left end point of a line
        // and then test if the angle is correct under the perspective of the middle point
        // (they should be the same in terms of line)
        var line = Line(DPos2.ORIGIN, DPos2(1.0, 0.0))
        var lineRotatedByCenter = line.copy().rotate(90.0.toRad())
        // default second parameter is by center
        println(lineRotatedByCenter.angleRotated.toDeg())
        var lineRotatedByLeft = line.copy().rotate(90.0.toRad(), DPos2.ORIGIN)
        Assertions.assertEquals(lineRotatedByCenter.angleRotated, lineRotatedByLeft.angleRotated, DOUBLE_PRECISION)

        // rotate the line from the right end point of a line
        var lineRotatedByRight = line.copy().rotate(90.0.toRad(), DPos2(1.0, 0.0))
        Assertions.assertEquals(lineRotatedByCenter.angleRotated, lineRotatedByRight.angleRotated, DOUBLE_PRECISION)
    }

    @org.junit.Test
    fun _rotate(){
        val rect = ColRect(DPos2(0, 0), DDim2(10, 10))
        for (i in 0..50){
            val rotAng = Math.random() * 2 * Math.PI
            rect.rotateAssign(rotAng)
            rect.rotateAssign(-rotAng)
        }
        println(rect)
    }
}