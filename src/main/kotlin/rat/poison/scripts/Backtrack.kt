package rat.poison.scripts

import com.badlogic.gdx.math.MathUtils.clamp
import org.jire.kna.MemoryCache
import org.jire.kna.float
import org.jire.kna.int
import rat.poison.curSettings
import rat.poison.game.*
import rat.poison.game.CSGO.clientDLL
import rat.poison.game.CSGO.csgoEXE
import rat.poison.game.CSGO.engineDLL
import rat.poison.game.entity.*
import rat.poison.game.forEntities
import rat.poison.game.netvars.NetVarOffsets
import rat.poison.game.netvars.NetVarOffsets.flSimulationTime
import rat.poison.game.offsets.ClientOffsets.dwIndex
import rat.poison.game.offsets.ClientOffsets.dwInput
import rat.poison.game.offsets.EngineOffsets.dwClientState_LastOutgoingCommand
import rat.poison.game.offsets.EngineOffsets.dwGlobalVars
import rat.poison.game.offsets.EngineOffsets.dwbSendPackets
import rat.poison.scripts.aim.*
import rat.poison.utils.*
import rat.poison.utils.Structs.*
import rat.poison.utils.extensions.uint
import rat.poison.utils.extensions.writeForced
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

var btRecords = Array(64) { Array(13) { BacktrackTable() } }

data class BacktrackTable(
	var simtime: Float = 0f,
	var headPos: Vector = vector(),
	var absPos: Vector = vector(),
	var alpha: Float = 100f
)

var bestBacktrackTarget = -1L

private var inBacktrack = false

private var haveGvars = false
var gvars = GlobalVars()

fun sendPacket(bool: Boolean) { //move outta here
	val byte = if (bool) 1.toByte() else 0.toByte()
	val memory = MemoryCache[1]
	memory.setByte(0, byte)
	
	engineDLL.writeForced(dwbSendPackets, memory, 1)
}

fun updateGVars() = every(15, true, inGameCheck = true) {
	if (me <= 0) return@every
	val tGvars = getGlobalVars()
	if (tGvars != null) {
		gvars = tGvars
		haveGvars = true
	} else {
		haveGvars = false
	}
}

fun setupBacktrack() = every(4, true, inGameCheck = true) {
	if (!curSettings.bool["ENABLE_BACKTRACK"] || me <= 0 || !haveGvars) return@every
	
	constructRecords()
}

fun attemptBacktrack(): Boolean {
	if (((curSettings.bool["BACKTRACK_SPOTTED"] && bestBacktrackTarget.spotted()) || !curSettings.bool["BACKTRACK_SPOTTED"]) && bestBacktrackTarget > 0L && haveGvars) {
		inBacktrack = true
		
		//Get/set vars
		if (!meCurWep.gun || !meCurWepEnt.canFire()) {
			inBacktrack = false; return false
		}
		
		val curSequenceNumber = csgoEXE.int(clientState + dwClientState_LastOutgoingCommand) + 1
		sendPacket(false)
		
		val input = memToInput(csgoEXE.read(clientDLL.address + dwInput, 253)!!)
		
		val userCMDptr = input.pCommands + (curSequenceNumber % 150) * 0x64L
		val verifiedUserCMDptr = input.pVerifiedCommands + (curSequenceNumber % 150) * 0x68L
		val oldUserCMDptr = input.pCommands + ((curSequenceNumber - 1) % 150) * 0x64L
		
		while (csgoEXE.int(userCMDptr + 0x4) < curSequenceNumber) {
			Thread.sleep(1)
		}
		
		//Check invalid?
		val oldUserCMD = memToUserCMD(csgoEXE.read(oldUserCMDptr, 100)!!)
		var userCMD = memToUserCMD(csgoEXE.read(userCMDptr, 100)!!)
		
		userCMD = fixUserCMD(userCMD, oldUserCMD)
		
		val bestTime = bestSimTime()
		
		if (bestTime == -1f) {
			sendPacket(true)
			inBacktrack = false
			return false
		}
		
		userCMD.iButtons = userCMD.iButtons or 1 // << 1 =|= IN_ATTACK
		userCMD.iTickCount = timeToTicks(bestTime)
		
		userCMDToMem(userCMDptr, userCMD)
		userCMDToMem(verifiedUserCMDptr, userCMD)
		
		sendPacket(true)
		inBacktrack = false
		Thread.sleep(4)
		return true
	}
	
	inBacktrack = false
	return false
}

private val boneMemory = threadLocalMemory(3984)

fun constructRecords() {
	var bestFov = 5F
	val clientAngle = clientState.angle()
	val meTeam = me.team()
	
	val boneMemory = boneMemory.get()
	
	forEntities(EntityType.CCSPlayer) {
		val ent = it.entity
		
		if (ent.dead() || ent == me || ent.team() == meTeam) return@forEntities
		
		if (ent.dormant()) { //Reset that bitch
			val entID = (csgoEXE.uint(ent + dwIndex) - 1).toInt()
			
			if (entID !in 0..64) return@forEntities
			
			for (i in 0 until 13) {
				val record = btRecords[entID][i]
				
				record.simtime = 0f
				record.alpha = 100f
				
				btRecords[entID][i] = record
			}
			
			return@forEntities
		}
		
		//Best target shit
		val pos = ent.bones(6)
		val fov = calcTarget(bestFov, bestBacktrackTarget, pos, clientAngle, 5F, 6, ovrStatic = true).fov
		if (fov < bestFov && fov > 0) {
			bestFov = fov
			bestBacktrackTarget = ent
		} else if (bestFov == 5F) {
			bestBacktrackTarget = -1L
		}
		
		//Create records
		val entSimTime = csgoEXE.float(ent + flSimulationTime)
		val entID = (csgoEXE.uint(ent + dwIndex) - 1).toInt()
		val tick = gvars.tickCount % 13
		
		if (entID in 0..63 && tick < 13) {
			val record = btRecords[entID][tick]
			
			csgoEXE.read(ent.boneMatrix(), boneMemory)
			record.headPos = boneMemory.bones(8).run { z(z + 5) }
			record.absPos = ent.absPosition().run { z(z - 5) }
			
			record.alpha = 100f
			record.simtime = entSimTime
			
			btRecords[entID][tick] = record
		}
		
		return@forEntities
	}
}

fun bestSimTime(): Float {
	if (bestBacktrackTarget <= 0L) {
		return -1f
	}
	
	var best = -1f
	val targetID = (csgoEXE.uint(bestBacktrackTarget + dwIndex) - 1).toInt()
	
	if (targetID < 0 || targetID > 63) return -1f
	
	val validRecords = getValidRecords(targetID)
	val minMaxIDX = getRangeRecords(targetID)
	
	if (minMaxIDX[0] == Int.MAX_VALUE || minMaxIDX[1] == -1) return -1f
	
	val minRecord = btRecords[targetID][minMaxIDX[0]]
	val maxRecord = btRecords[targetID][minMaxIDX[1]]
	
	val minHeadPos = worldToScreen(minRecord.headPos)
	val minAbsPos = worldToScreen(minRecord.absPos)
	val maxHeadPos = worldToScreen(maxRecord.headPos)
	val maxAbsPos = worldToScreen(maxRecord.absPos)
	
	if (minHeadPos.w2s() && minAbsPos.w2s() && maxHeadPos.w2s() && maxAbsPos.w2s()) {
		val w = (minAbsPos.y - minHeadPos.y) / 4F
		val minMidX = (minAbsPos.x + minHeadPos.x) / 2F
		val maxMidX = (maxAbsPos.x + maxAbsPos.x) / 2F
		
		var sign = -1
		
		if (minMidX > maxMidX) {
			sign = 1
		}
		
		val topLeft = vector(minHeadPos.x - (w / 3F) * sign, minHeadPos.y, 0F)
		val topRight = vector(maxHeadPos.x + (w / 3F) * sign, maxHeadPos.y, 0F)
		
		val bottomLeft = vector(minMidX - (w / 2F) * sign, minAbsPos.y + 8F, 0F)
		//val bottomRight = vector(maxMidX + (w / 2F) * sign, maxAbsPos.y+8F, 0F)
		
		val punch = me.punch()
		val curFov = csgoEXE.int(me + NetVarOffsets.m_iDefaultFov)
		val rccFov1 =
			atan((CSGO.gameWidth.toFloat() / CSGO.gameHeight.toFloat()) * 0.75 * tan(Math.toRadians(curFov / 2.0)))
		val rccFov2 = (CSGO.gameWidth / 2) / tan(rccFov1).toFloat()
		
		val centerX = (CSGO.gameWidth / 2) - tan(Math.toRadians(punch.y.toDouble())).toFloat() * rccFov2
		val centerY = (CSGO.gameHeight / 2) - tan(Math.toRadians(punch.x.toDouble())).toFloat() * rccFov2
		
		if (inRange(centerX, topLeft.x, topRight.x) && inRange(
				centerY,
				topLeft.y,
				bottomLeft.y
			)
		) {//If middle of screen + recoil is inside polygon
			var bestMinX = Float.MAX_VALUE
			
			for (i in validRecords) {
				val record = btRecords[targetID][i]
				val w2s = worldToScreen(record.headPos)
				
				val centerDist = abs(centerX - w2s.x)
				if (centerDist < bestMinX) {
					bestMinX = centerDist
					
					best = record.simtime
				}
			}
		}
	}
	
	return best
}

fun isValidTick(tick: Int): Boolean {
	if (!haveGvars) return false
	
	val delta = gvars.tickCount - tick
	val deltaTime = delta * gvars.intervalPerTick
	
	var backtrackMS = curSettings["${curWepCategory}_BACKTRACK_MS"].toFloat()
	if (curWepOverride && curWepSettings.tBacktrack) {
		backtrackMS = curWepSettings.tBTMS.toFloat()
	}
	
	val max = clamp(backtrackMS / 1000f, 0F, .19F)
	
	return abs(deltaTime) <= max
}

fun timeToTicks(time: Float): Int {
	return (.5f + time / gvars.intervalPerTick).toInt()
}

fun getRangeRecords(entID: Int, minIDX: Int = 0, maxIDX: Int = 13): Array<Int> {
	var youngestSimtime = Float.MAX_VALUE
	var oldestSimtime = 0F
	val minMaxIDX = arrayOf(Int.MAX_VALUE, -1)
	
	for (i in minIDX until maxIDX) {
		val record = btRecords[entID][i]
		
		if (isValidTick(timeToTicks(record.simtime))) {
			if (record.simtime > oldestSimtime) {
				oldestSimtime = record.simtime
				minMaxIDX[1] = i
			}
			
			if (record.simtime < youngestSimtime) {
				youngestSimtime = record.simtime
				minMaxIDX[0] = i
			}
		}
	}
	
	return minMaxIDX
}

fun getValidRecords(entID: Int): List<Int> {
	val recordsList = mutableListOf<Int>()
	
	if (entID in 0..63) {
		for (i in 0 until 13) {
			if (isValidTick(timeToTicks(btRecords[entID][i].simtime))) {
				recordsList.add(i)
			}
		}
	}
	
	return recordsList
}

fun getGlobalVars(): GlobalVars? {
	val mem = csgoEXE.read(engineDLL.address + dwGlobalVars, 64)
	if (mem != null) {
		return memToGlobalVars(mem)
	}
	
	return null
}

fun inRange(value: Float, num1: Float, num2: Float): Boolean {
	val min: Float
	val max: Float
	
	if (num1 > num2) {
		max = num1
		min = num2
	} else {
		max = num2
		min = num1
	}
	
	return value > min && value < max
}
