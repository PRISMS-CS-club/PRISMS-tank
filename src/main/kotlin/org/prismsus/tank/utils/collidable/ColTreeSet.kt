package org.prismsus.tank.utils.collidable

import org.prismsus.tank.utils.*
import java.awt.Color
import java.awt.Shape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * collection of collidables, implemented by a quad tree
 *
 */
class ColTreeSet(val dep: Int, val bound: ColAARect) {
    companion object {
        const val MAX_OBJECT = 4
        const val MAX_DEP = 8
    }

    var cols = ArrayList<Collidable>()
    var subTrees: Array<ColTreeSet>? = null
    var size = 0
    // indexed by quadrant
    var topLeftSub: ColTreeSet?
        get() = subTrees?.get(1)
        set(value) {
            value?.let { subTrees?.set(1, it) }
        }
    var topRightSub: ColTreeSet?
        get() = subTrees?.get(0)
        set(value) {
            value?.let { subTrees?.set(0, it) }
        }
    var bottomRightSub: ColTreeSet?
        get() = subTrees?.get(3)
        set(value) {
            value?.let { subTrees?.set(3, it) }
        }
    var bottomLeftSub: ColTreeSet?
        get() = subTrees?.get(2)
        set(value) {
            value?.let { subTrees?.set(2, it) }
        }
    val allSubCols: ArrayList<Collidable>
        get() {
            val res: ArrayList<Collidable> = cols.clone() as ArrayList<Collidable>
            subTrees?.forEach { res.addAll(it.allSubCols) }
            assert(res.size == size) { "allSubCols=${res.size}, size=$size" }
            return res
        }
    val allSubPartitionLines: ArrayList<Line>
        get() {
            val ret = ArrayList<Line>()
            subTrees?.forEach { ret.addAll(it.allSubPartitionLines) }

            ret.add(Line(bound.topLeftPt, bound.topRightPt))
            ret.add(Line(bound.topRightPt, bound.bottomRightPt))
            ret.add(Line(bound.bottomRightPt, bound.bottomLeftPt))
            ret.add(Line(bound.bottomLeftPt, bound.topLeftPt))

            return ret
        }
    fun checkColsInBound() : Boolean{
        cols.forEach {
            if (!(bound enclose it)){
                return false
//                assert(false)
            }
        }
        var ret = true
        subTrees?.forEach {
            if (!it.checkColsInBound()){
                ret = false
            }
        }
        return ret
    }

    /**
     * delete all the collidables in this subtree, including the collidables stored in
     * this node and the collidables stored in the subtrees.
     * */
    fun clearAll() {
        cols.clear()
        for (i in 0..3) {
            subTrees?.get(i)?.clearAll()
        }
        subTrees = null
    }

    /**
     * When one node exceeds the max object number, split it into four subtrees
     * */
    private fun split() {
        if(subTrees != null) {
            // only split when this node have not been split
            return
        }
        // make each of the four subtrees slightly larger than bound.size / 2
        // so that there will be no gap between the four subtrees
        val offset = DOUBLE_PRECISION * 100.0
        val tlShift = DVec2(offset, offset) * 10.0
        val subDim = (bound.size / 2.0)
        val topLeft = bound.topLeftPt - tlShift.xVec + tlShift.yVec
        val quad2 = ColTreeSet(dep + 1, ColAARect.byTopLeft(topLeft, subDim + tlShift * 2.0))
        val quad1 = ColTreeSet(dep + 1, ColAARect.byTopLeft(topLeft + (subDim.xVec - tlShift.xVec), subDim + tlShift * 2.0 ))
        val quad4 = ColTreeSet(dep + 1, ColAARect.byTopLeft(topLeft - (subDim.yVec - tlShift.yVec) + (subDim.xVec - tlShift.xVec), subDim + tlShift * 2.0))
        val quad3 = ColTreeSet(dep + 1, ColAARect.byTopLeft(topLeft - (subDim.yVec - tlShift.yVec), subDim + tlShift * 2.0 ))
        subTrees = arrayOf(quad1, quad2, quad3, quad4)
    }


    /**
     *  calculate which subtree(quadrant) the AARect can be put in
     *  if none of the subtrees can completely contain the AARect or this node have not been splitted, return null
     * */
    fun subTreeBelongTo(box: ColAARect): ColTreeSet? {
        return subTrees?.let {
            for (sub in it) {
                if (sub.bound.enclose(box)) {
                    return sub
                }
            }
            return null
        }
    }

    /**
     * Insert a new collidable object into the quad-tree.
     * @param col The object to be inserted.
     * @return true if the insertion is successful, false otherwise.
     */
    fun insert(col: Collidable) : Boolean {
        if (!(bound enclose col))
            return false
        val belongTo = subTreeBelongTo(col.encAARect)
        if (belongTo != null) {
            assert(belongTo.insert(col))
            size++
            return true
        }
        cols.add(col)
        size++
        if(this.subTrees == null && cols.size > MAX_OBJECT && dep < MAX_DEP) {
            split()
            val toRemove = ArrayList<Collidable>()
            for (c in cols) {
                val belongTo = subTreeBelongTo(c.encAARect)
                if (belongTo != null) {
                    assert(belongTo.insert(c))
                    toRemove.add(c)
                }
            }
            for (c in toRemove) {
                cols.remove(c)
            }
        }
        return true
    }

    fun corespondingAARect(col : Collidable) : ColAARect{
        val belongTo = subTreeBelongTo(col.encAARect)
        if (belongTo != null) {
            return belongTo.corespondingAARect(col)
        }
        return bound
    }

    /**
     * Remove one collidable object from the collision quad-tree.
     * @param col The object to be removed.
     * @return True if the object is successfully removed, false if the object is not found.
     */
    fun remove(col: Collidable): Boolean {
        val belongTo = subTreeBelongTo(col.encAARect)
        if (belongTo != null) {
            if(!belongTo.remove(col)) {
                println("remove failed")
                throw Exception()
            }
            size--
            return true
        }
        if (cols.contains(col)) {
            cols.remove(col)
            size--
            return true
        }
        return false
    }

    fun toShapes(coordTransform: (DPos2) -> DPos2 = { it }, shapeModifier: (Shape) -> Unit = { it }): ArrayList<Shape> {
        val ret = ArrayList<Shape>()
        for (col in allSubCols) {
            val shape = col.toShape(coordTransform)
            shapeModifier(shape)
            ret.add(shape)
        }
        return ret
    }


    infix fun possibleCollision(col: Collidable): ArrayList<Collidable> {
        val belongTo = subTreeBelongTo(col.encAARect)
        val ret = ArrayList<Collidable>()
        ret.addAll(cols) // all unsplittable collidable objects
        if (belongTo != null) {
            ret.addAll(belongTo.possibleCollision(col))
        } else {
        // add all the cols in the smallest square that can fit the col
            ret.clear()
            ret.addAll(allSubCols)
        }
        return ret
    }

    infix fun collide(col: Collidable): Boolean {
        return collidedObjs(col).isNotEmpty()
    }

    infix fun collidedObjs(col: Collidable): ArrayList<Collidable> {
        val possible = possibleCollision(col)
        val ret = ArrayList<Collidable>()
        for (c in possible) {
            if (c.collide(col) && c != col) {
                ret.add(c)
            }
        }
        return ret
    }


    fun getCoordPanel(panelSiz : IDim2) : CoordPanel {
        var maxPos = DPos2(Double.MIN_VALUE / 2, Double.MIN_VALUE / 2)
        var minPos = DPos2(Double.MAX_VALUE / 2, Double.MAX_VALUE / 2)
        for (col in allSubCols){
            maxPos = maxPos max col.encAARect.topRightPt
            minPos = minPos min col.encAARect.bottomLeftPt
        }
        val xsz = max(abs(maxPos.x), abs(minPos.x)) * 2
        val ysz = max(abs(maxPos.y), abs(minPos.y)) * 2
        val pfactor = min(panelSiz.x / xsz, panelSiz.y / ysz)
        // make sure that the actual interval between grids is at least 30pixel
        // actual interval = pinterv * pfactor
        val pinterv = ceil(max(30.0 / pfactor, 1.0)).toInt()
        val panel = CoordPanel(IDim2(pinterv, pinterv), IDim2(pfactor.toInt(), pfactor.toInt()), panelSiz)
        for (col in allSubCols){
            panel.drawCollidable(col)
        }
        for (line in allSubPartitionLines){
            panel.graphicsModifier = {
                g -> g.color = Color.RED
            }
            panel.drawCollidable(line)
        }
        return panel
    }
}