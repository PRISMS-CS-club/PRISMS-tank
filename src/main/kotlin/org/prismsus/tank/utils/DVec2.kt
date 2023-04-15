package org.prismsus.tank.utils

import kotlin.math.*

/**
 * A 2D vector with double coordinates.
 * @property x The x coordinate.
 * @property y The y coordinate.
 */
class DVec2(var x: Double, var y: Double) {
    constructor(): this(0.0, 0.0)
    constructor(vec: IVec2): this(vec.x.toDouble(), vec.y.toDouble())

    /**
     * Add this double vector and another double vector. Returns a new object.
     * @param other The other double vector.
     * @return The sum vector.
     */
    operator fun plus(other: DVec2): DVec2 {
        return DVec2(x + other.x, y + other.y)
    }
    operator fun plusAssign(other: DVec2) {
        x += other.x
        y += other.y
    }

    /**
     * Subtract this double vector and another double vector. Returns a new object.
     * @param other The other double vector.
     * @return The subtracted vector.
     */
    operator fun minus(other: DVec2): DVec2 {
        return DVec2(x - other.x, y - other.y)
    }

    operator fun minusAssign(other: DVec2) {
        x -= other.x
        y -= other.y
    }

    operator fun unaryMinus(): DVec2 {
        return DVec2(-x, -y)
    }

    operator fun unaryPlus(): DVec2 {
        return DVec2(x, y)
    }

    /**
     * Multiply this double vector and a scalar. Returns a new object.
     * @param other The scalar.
     * @return The product vector.
     */
    operator fun times(other: Double): DVec2 {
        return DVec2(x * other, y * other)
    }
    operator fun timesAssign(other: Double) {
        x *= other
        y *= other
    }
    /**
     * Divide this double vector and a scalar. Returns a new object.
     * @param other The scalar.
     * @return The divided vector.
     */
    operator fun div(other: Double): DVec2 {
        return DVec2(x / other, y / other)
    }
    operator fun divAssign(other: Double) {
        x /= other
        y /= other
    }

    /**
     * Get the length of the vector.
     * @return The length of the vector.
     */
    fun length(): Double {
        return sqrt(x * x + y * y)
    }

    /**
     * Get the normalized vector.
     * @return The normalized vector.
     */
    fun norm(): DVec2 {
        return this / length()
    }

    /**
     * Get the dot product of this vector and another vector.
     * @param other The other vector.
     * @return The dot product.
     */
    fun dot(other: DVec2): Double {
        return x * other.x + y * other.y
    }

    /**
     * Get the cross product of this vector and another vector.
     * @param other The other vector.
     * @return The cross product.
     */
    fun cross(other: DVec2): Double {
        return x * other.y - y * other.x
    }

    /**
     * Round the vector to the nearest integer vector.
     * @return The rounded vector.
     */
    fun round(): IVec2 {
        return IVec2(x.roundToInt(), y.roundToInt())
    }

    /**
     * Rotate the angle to the given angle
     * @param angle The angle in radiance to rotate to.
     * @return The rotated vector.
     */

    fun rotateTo(rad : Double): DVec2 {
        val len = length()
        val nx = len * cos(rad)
        val ny = len * sin(rad)
        return DVec2(nx, ny)
    }

    /**
     * Get the angle of the vector.
     * @return The angle of the vector.
     */

    fun angle(): Double {
        return atan2(y, x)
    }

    /**
     * Make the vector turn in counter-clockwise direction for certain angle.
     * @param radOffset The angle in radian to turn
     * @return The turned vector.
     */
    fun rotate(radOffset : Double) : DVec2{
        val curAngle = angle()
        val newAngle = curAngle + radOffset
        return rotateTo(newAngle)
    }

    companion object {

        /**
         * convert radian to degree
         * @param rad The radian.
         * @return The degree.
         */

        fun toDeg(rad : Double) : Double {
            return rad * 180 / PI
        }

        /**
         * convert degree to radian
         * @param deg degree
         * @return radian
         */
        fun toRad(deg : Double) : Double {
            return deg * PI / 180
        }

        /**
        * create a vector from angle and length
        * @param angle in rad
        * @param length the length of the vector
        * @return the vector created
        */
        fun byPolarCoord(len : Double, rad : Double) : DVec2 {
            return DVec2(len * cos(rad), len * sin(rad))
        }
    }
}