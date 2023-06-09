package org.prismsus.tank.game

import org.prismsus.tank.bot.*
import org.prismsus.tank.elements.GameElement
import org.prismsus.tank.elements.GameMap
import org.prismsus.tank.elements.MovableElement
import org.prismsus.tank.elements.Tank
import org.prismsus.tank.event.*
import org.prismsus.tank.game.OtherRequests.*
import org.prismsus.tank.game.TankWeaponInfo.*
import org.prismsus.tank.networkings.GuiCommunicator
import org.prismsus.tank.utils.*
import org.prismsus.tank.utils.nextUid
import org.prismsus.tank.utils.collidable.ColMultiPart
import org.prismsus.tank.utils.collidable.DPos2
import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.PI

class Game(val replayFile: File, val map: GameMap, vararg val bots: GameBot) {
    val humanPlayerBots: Array<HumanPlayerBot> = bots.filterIsInstance<HumanPlayerBot>().toTypedArray()
    val eventHistoryToSave = PriorityBlockingQueue<GameEvent>()
    var controllers: Array<FutureController>
    val requestsQ = PriorityBlockingQueue<ControllerRequest<Any>>()
    val cidToTank = mutableMapOf<Long, Tank?>()
    val tankToCid = mutableMapOf<Tank, Long>()
    val lastCollidedEle = mutableMapOf<GameElement, ArrayList<GameElement>>()
    val botThs: Array<Thread?> = Array(bots.size) { null }
    var replayTh: Thread
    val gameInitMs = System.currentTimeMillis()
    val elapsedGameMs: Long
        get() = System.currentTimeMillis() - gameInitMs
    var lastGameLoopMs = elapsedGameMs

    init {
        controllers = Array(bots.size) { i -> FutureController(i.toLong(), requestsQ) }
        processNewEvent(MapCreateEvent(map, elapsedGameMs))
        for ((i, c) in controllers.withIndex()) {
            val tank = Tank.byInitPos(nextUid, DPos2.ORIGIN, bots[i].name)
            val tankPos = map.getUnoccupiedRandPos(tank.colPoly)!!
            (tank.colPoly as ColMultiPart).baseColPoly.rotationCenter = tankPos
//            val tPanel = CoordPanel(IDim2(1, 1), IDim2(50, 50))
//            tPanel.drawCollidable(tank.colPoly)
//            tPanel.showFrame()
//            tPanel.showFrame()
            map.addEle(tank)
            processNewEvent(ElementCreateEvent(tank, elapsedGameMs))
            cidToTank[c.cid] = tank
            tankToCid[tank] = c.cid
            game = this
        }

//        val panel = map.quadTree.getCoordPanel(IDim2(1000, 1000))
//        panel.showFrame()

        for ((i, bot) in bots.withIndex()) {
            botThs[i] =
                Thread {
                    if (bot.isFutureController)
                        bot.loop(controllers[i])
                    else
                        bot.loop(Controller(controllers[i]))
                }
            botThs[i]!!.start()
        }
        replayTh = Thread {
            replaySaver()
        }
        replayFile.appendText("[\n")
        replayTh.start()
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
    }

    private fun tankWeaponInfoHandler(req: ControllerRequest<Any>): Any {
        val tk = cidToTank[req.cid]!!
        when (req.requestType) {
            TANK_HP -> {
                return tk.hp
            }

            TANK_MAX_HP -> {
                // TODO: add max hp field in tank
                return INIT_TANK_HP
            }

            TANK_LTRACK_SPEED -> {
                return tk.leftTrackVelo
            }

            TANK_RTRACK_SPEED -> {
                return tk.rightTrackVelo
            }

            TANK_TRACK_MAX_SPEED -> {
                return tk.trackMaxSpeed
            }

            TANK_COLBOX -> {
                return tk.tankRectBox
            }

            TANK_POS -> {
                return tk.colPoly.rotationCenter
            }

            TANK_ANGLE -> {
                // add because when angle=0, the tank is facing up
                return (tk.colPoly.angleRotated + PI / 2) % (2 * PI)
            }

            TANK_VIS_RANGE -> {
                return tk.visibleRange
            }

            WEAPON_RELOAD_RATE_PER_SEC -> {
                return tk.weapon.reloadRate
            }

            WEAPON_MAX_CAPACITY -> {
                return tk.weapon.maxCapacity
            }

            WEAPON_CUR_CAPACITY -> {
                return tk.weapon.curCapacity
            }

            WEAPON_DAMAGE -> {
                return tk.weapon.damage
            }

            WEAPON_COLBOX -> {
                return tk.weapon.colPoly
            }

            COMBINED_COLBOX -> {
                return tk.colPoly
            }

            BULLET_COLBOX -> {
                // TODO: add bullet colbox as a field in weapon
                return INIT_BULLET_COLBOX
            }

            BULLET_SPEED -> {
                // TODO: add bullet speed as a field in weapon
                return INIT_BULLET_SPEED
            }

            else -> {
                throw IllegalArgumentException("Invalid request type")
            }
        }
    }

    private fun replaySaver() {
        // read from eventHistory, save to file
        try {
            while (true) {
                Thread.sleep(100)
                while (eventHistoryToSave.isNotEmpty()) {
                    val curEvent = eventHistoryToSave.poll()
                    replayFile.appendBytes(curEvent.serializedBytes + ",\n".toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: InterruptedException) {
            return
        }
    }

    private fun handleOtherRequests(req: ControllerRequest<Any>) {
        val tk = cidToTank[req.cid]!!
        when (req.requestType) {
            GET_VISIBLE_ELEMENTS -> {
                req.returnTo!!.complete(
                    ArrayList(map.gameEles).filter {
                        val dis = it.colPoly.rotationCenter.dis(tk.colPoly.rotationCenter)
                        dis <= tk.visibleRange && it != tk
                    }
                )
            }

            GET_VISIBLE_TANKS -> {
                req.returnTo!!.complete(
                    ArrayList(map.tanks).filter {
                        val dis = it.colPoly.rotationCenter.dis(tk.colPoly.rotationCenter)
                        dis <= tk.visibleRange && it != tk
                    }
                )
            }

            GET_VISIBLE_BULLETS -> {
                req.returnTo!!.complete(
                    ArrayList(map.bullets).filter {
                        val dis = it.colPoly.rotationCenter.dis(tk.colPoly.rotationCenter)
                        dis <= tk.visibleRange
                    }
                )
            }

            CHECK_BLOCK_AT -> {
                val pos = req.params!!.first() as IPos2
                if (tk.colPoly.rotationCenter.dis(pos.toDPos2()) > tk.visibleRange) {
                    req.returnTo!!.complete(null)
                    return
                }
                val ret = map.blocks[pos.x][pos.y]
                req.returnTo!!.complete(ret)
            }

            CHECK_COLLIDING_GAME_ELES -> {
                val ret = lastCollidedEle[tk]?.filter {
                    val dis = it.colPoly.rotationCenter.dis(tk.colPoly.rotationCenter)
                    dis <= tk.visibleRange
                }
                req.returnTo!!.complete(ret)
            }

            GET_VISITED_ELEMENTS -> {
                // TODO: implement this
                req.returnTo!!.complete(ArrayList<GameElement>())
            }

            FIRE -> {

                val tankWeaponDirAng = tk.colPoly.angleRotated + PI / 2
                val bullet = tk.weapon.fire(tankWeaponDirAng)

                if (bullet != null) {
                    if (map.quadTree.allSubCols.contains(bullet.colPoly))
                        assert(false)
                    map.addEle(bullet)
                    processNewEvent(ElementCreateEvent(bullet, elapsedGameMs))
                }
            }

            SET_LTRACK_SPEED -> {
                val target = req.params!!.first() as Double
                cidToTank[req.cid]!!.leftTrackVelo = target
            }

            SET_RTRACK_SPEED -> {
                val target = req.params!!.first() as Double
                cidToTank[req.cid]!!.rightTrackVelo = target
            }
        }
    }

    private fun processNewEvent(evt: GameEvent) {
        eventHistoryToSave.add(evt)
        for (hbot in humanPlayerBots) {
            hbot.evtsToClnt.add(evt)
        }
    }

    private fun handleRequests() {
        while (!requestsQ.isEmpty()) {
            val curReq = requestsQ.poll()
            if (curReq.cid !in cidToTank)
                continue
            when (curReq.requestType) {
                is TankWeaponInfo -> {
                    val ret = tankWeaponInfoHandler(curReq)
                    curReq.returnTo!!.complete(ret)
                }

                is OtherRequests -> {
                    handleOtherRequests(curReq)
                }

                else -> {
                    throw IllegalArgumentException("Invalid request type")
                }
            }
        }
    }

    private fun handleUpdatableElements(): ArrayList<GameElement> {
        val dt = elapsedGameMs - lastGameLoopMs
        val toRemove = ArrayList<GameElement>()
        for (updatable in map.timeUpdatables) {
            if (updatable is MovableElement && updatable.willMove(dt)) {
                if (updatable.colPoly is ColMultiPart)
                    (updatable.colPoly as ColMultiPart).checks()
                val colPolyAfterMove = updatable.colPolyAfterMove(dt)
                val collideds = map.quadTree.collidedObjs(colPolyAfterMove)
                collideds.remove(updatable.colPoly)
                val prevHp = updatable.hp
                var curHp = updatable.hp
                for (collided in collideds) {
                    val otherGe = map.collidableToEle[collided]!!
                    updatable.processCollision(otherGe)
                    lastCollidedEle.getOrPut(updatable) { ArrayList() }.add(otherGe)
                    lastCollidedEle.getOrPut(otherGe) { ArrayList() }.add(updatable)
                    curHp = updatable.hp
                    val hpChanged = otherGe.processCollision(updatable)
                    if (hpChanged) {
                        processNewEvent(
                            ElementUpdateEvent(
                                otherGe,
                                UpdateEventMask.defaultFalse(
                                    hp = true
                                ),
                                elapsedGameMs
                            )
                        )
                        if (otherGe.removeStat == GameElement.RemoveStat.TO_REMOVE) {
                            toRemove.add(otherGe)
                        }
                    }
                }

                if (updatable.removeStat == GameElement.RemoveStat.TO_REMOVE) {
                    toRemove.add(updatable)
                }

                if (collideds.isNotEmpty()) {
                    continue
                }

                val prevPos =
                    if (updatable.colPoly is ColMultiPart) (updatable.colPoly as ColMultiPart).baseColPoly.rotationCenter else updatable.colPoly.rotationCenter
                val prevAng = updatable.colPoly.angleRotated
                val curPos =
                    if (colPolyAfterMove is ColMultiPart) (colPolyAfterMove).baseColPoly.rotationCenter else colPolyAfterMove.rotationCenter
                val curAng = colPolyAfterMove.angleRotated
                if (collideds.isEmpty() && (prevPos != curPos || prevAng != curAng)) {
                    assert(map.quadTree.remove(updatable.colPoly))
                    updatable.colPoly.becomeNonCopy(colPolyAfterMove)
                    map.quadTree.insert(updatable.colPoly)
//                        println("cur ang: ${updatable.colPoly.angleRotated}")
                    processNewEvent(
                        ElementUpdateEvent(
                            updatable,
                            UpdateEventMask.defaultFalse(
                                x = (prevPos.x errNE curPos.x),
                                y = (prevPos.y errNE curPos.y),
                                rad = (prevAng errNE curAng),
                                hp = (prevHp != curHp)
                            ), elapsedGameMs
                        )
                    )
                }
            } else {
                updatable.updateByTime(dt)
            }
        }
        for (entry in lastCollidedEle) {
            lastCollidedEle[entry.key] = ArrayList(entry.value.distinct())
        }
        return ArrayList(toRemove.distinct())
    }

    fun start() {
        lastGameLoopMs = elapsedGameMs
        while (true) {
            // first handle all the requests, then move all the elements
            val loopStartMs = elapsedGameMs
            handleRequests()
            val toRemove = handleUpdatableElements()
            for (rem in toRemove) {
                map.remEle(rem)
                if (rem is Tank) {
                    val cid = tankToCid[rem]!!
                    val bot = bots[cid.toInt()]
                    cidToTank.remove(cid)
                    tankToCid.remove(rem)
                    if (bot !is HumanPlayerBot)
                        botThs[cid.toInt()]!!.interrupt()
                }
                processNewEvent(ElementRemoveEvent(rem.uid, elapsedGameMs))
            }
            val loopEndMs = elapsedGameMs
            val loopLen = loopEndMs - loopStartMs
//            println("cur loop len: $loopLen, slept for ${DEF_MS_PER_LOOP - loopLen}")
            lastGameLoopMs = elapsedGameMs
            if (loopLen < DEF_MS_PER_LOOP)
                Thread.sleep(DEF_MS_PER_LOOP - loopLen)
        }
    }


    fun stop() {
        // interrupt all the bots
        print("closing bot threads...")
        for (botTh in botThs) {
            botTh!!.interrupt()
        }
        println("done")
        // interrupt the replay saver
        print("closing replay saver thread...")
        replayTh.interrupt()
        println("done")
        // delete the trailing comma
        val fileContent = replayFile.readText().toMutableList()
        if (fileContent.lastIndex >= 1)
            fileContent.removeAt(fileContent.lastIndex - 1)
        replayFile.writeText(fileContent.joinToString(""))
        // write the ending ] and close the replay file
        println("saving replay file...")
        replayFile.appendText("]")
        println("replay file saved")
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var curTime = LocalDate.now().toString() + "_" + LocalTime.now().toString()
            // create a new file of this name
            // replace all : with -
            curTime = curTime.replace(':', '-')
            val replayFile = File("./replayFiles/replay_@$curTime.json")

            println(Paths.get(replayFile.path.toString()).toAbsolutePath())
            replayFile.createNewFile()
            val communicator = GuiCommunicator(1)
            communicator.start()
            val players = communicator.humanPlayerBots.get()
            val randBots = Array(1) { RandomMovingBot() }
            val aimingBots = Array(1) { TankAimingBot() }
            val game = Game(replayFile, GameMap("15x15.json"), *aimingBots, *randBots, *players.toTypedArray())
            game.start()

        }
    }
}