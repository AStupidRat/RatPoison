package rat.poison.scripts.aim

import rat.poison.curSettings
import rat.poison.game.*
import rat.poison.game.entity.*
import rat.poison.settings.*
import rat.poison.utils.*
import rat.poison.utils.generalUtil.has
import rat.poison.utils.generalUtil.stringToIntList
import java.lang.Math.toRadians
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

var target = -1L
var canPerfect = false
var boneTrig = false
var destBone = -1

fun reset(resetTarget: Boolean = true) {
	destBone = -5
	if (resetTarget) {
		target = -1L
	}
	canPerfect = false
}

data class FindTargetResult(var player: Player = -1L, var closestBone: Int = -1) {
	fun reset() {
		this.player = -1
		this.closestBone = -1
	}
}

data class CalcTargetResult(var fov: Float = -1F, var delta: Float = -1F, var player: Player = -1) {
	fun reset() {
		this.fov = -1F
		this.delta = -1F
		this.player = -1
	}
}

val findTargetResult = ThreadLocal.withInitial { FindTargetResult() }
val calcTargetResult = ThreadLocal.withInitial { CalcTargetResult() }

fun findTarget(position: Angle, angle: Angle, allowPerfect: Boolean,
			   lockFOV: Float = curSettings.float["AIM_FOV"], BONE: String = curSettings["AIM_BONE"], visCheck: Boolean = true, teamCheck: Boolean = true): FindTargetResult {
	val result = findTargetResult.get()
	result.reset()
	var closestFOV = Float.MAX_VALUE
	var closestDelta = Float.MAX_VALUE
	var closestPlayer = -1L
	var closestBone = -1

	var bones = BONE.stringToIntList()
	val findNearest = bones.has { it as Int <= 0 }

	forEntities(EntityType.CCSPlayer) {
		val entity = it.entity
		if (entity <= 0 || entity == me || !entity.canShoot(visCheck, teamCheck)) {
			return@forEntities
		}

		if (findNearest) bones = mutableListOf(entity.nearestBone())
		bones.forEach { bone ->

			val arr = calcTarget(closestDelta, entity, position, angle, lockFOV, bone)

			val fov = arr.fov

			if (fov > 0F) {
				closestFOV = fov
				closestDelta = arr.delta
				closestPlayer = arr.player
				closestBone = bone
			}
		}
	}

	if (closestDelta == Float.MAX_VALUE || closestDelta < 0 || closestPlayer < 0) return result

	val randInt = randInt(1, 100)

	if (curSettings.bool["PERFECT_AIM"] && allowPerfect && closestFOV <= curSettings.float["PERFECT_AIM_FOV"] && randInt <= curSettings.int["PERFECT_AIM_CHANCE"]) {
		canPerfect = true
	}
	result.player = closestPlayer
	result.closestBone = closestBone

	return result
}

fun calcTarget(calcClosestDelta: Float, entity: Entity, position: Angle, curAngle: Angle, lockFOV: Float = curSettings.float["AIM_FOV"], BONE: Int, ovrStatic: Boolean = false): CalcTargetResult {
	val result = calcTargetResult.get()
	result.reset()

	var ePos: Angle = entity.bones(BONE)

	if (ovrStatic) {
		ePos = position
	}

	if (curSettings["FOV_TYPE"].replace("\"", "") == "DISTANCE" && !ovrStatic) {
		val distance = position.distanceTo(ePos)

		val dest = getCalculatedAngle(me, ePos)

		val pitchDiff = abs(curAngle.x - dest.x)
		var yawDiff = abs(curAngle.y - dest.y)

		if (yawDiff > 180f) {
			yawDiff = 360f - yawDiff
		}

		val fov = abs(sin(toRadians(yawDiff.toDouble())) * distance)
		val delta = abs((sin(toRadians(pitchDiff.toDouble())) + sin(toRadians(yawDiff.toDouble()))) * distance)

		if (delta <= lockFOV && delta <= calcClosestDelta) {
			result.fov = fov.toFloat()
			result.delta = delta.toFloat()
			result.player = entity
		}
	} else {
		val calcAng = realCalcAngle(me, ePos)

		val delta = Angle(curAngle.x - calcAng.x, curAngle.y - calcAng.y, 0F)
		delta.normalize()

		val fov = sqrt(delta.x.pow(2F) + delta.y.pow(2F))

		if (fov <= lockFOV && fov <= calcClosestDelta) {
			result.fov = fov
			result.delta = fov
			result.player = entity
		}
	}

	return result
}

fun Entity.inMyTeam() =
		!curSettings.bool["TEAMMATES_ARE_ENEMIES"] && if (DANGER_ZONE) {
			me.survivalTeam().let { it > -1 && it == this.survivalTeam() }
		} else me.team() == team()

fun Entity.canShoot(visCheck: Boolean = true, teamCheck: Boolean = true) = ((if (DANGER_ZONE) { true } else if (visCheck) { spotted() || (curSettings.bool["TEAMMATES_ARE_ENEMIES"] && team() == me.team() || !teamCheck) } else { true })
		&& !dormant()
		&& !dead()
		&& (!inMyTeam() || !teamCheck)
		&& !isProtected()
		&& !meDead)

internal inline fun <R> aimScript(duration: Int, crossinline precheck: () -> Boolean,
								  crossinline doAim: (destinationAngle: Angle,
													  currentAngle: Angle, aimSpeed: Int, aimSpeedDivisor: Int) -> R) = every(duration) {
	if (!precheck()) return@every
	if (!curSettings.bool["ENABLE_AIM"]) return@every

	val canFire = meCurWepEnt.canFire()
	if (meCurWep.grenade || meCurWep.knife || meCurWep.miscEnt || meCurWep == Weapons.ZEUS_X27 || meCurWep.bomb || meCurWep == Weapons.NONE) { //Invalid for aimbot
		reset()
		return@every
	}

	if (curSettings.bool["AIM_ONLY_ON_SHOT"] && (!canFire || (didShoot && !meCurWep.automatic && !curSettings.bool["AUTOMATIC_WEAPONS"]))) { //Onshot
		reset(false)
		return@every
	}

	if (meCurWep.sniper && !me.isScoped() && curSettings.bool["ENABLE_SCOPED_ONLY"]) { //Scoped only
		reset()
		return@every
	}

	val aim = curSettings.bool["ACTIVATE_FROM_AIM_KEY"] && keyPressed(AIM_KEY)
	val pressedForceAimKey = keyPressed(curSettings.int["FORCE_AIM_KEY"])
	val forceAim = pressedForceAimKey || curSettings.bool["FORCE_AIM_ALWAYS"]
	val haveAmmo = meCurWepEnt.bullets() > 0

	val pressed = ((aim || boneTrig) && !MENUTOG && haveAmmo) || forceAim

	if (!pressed) {
		reset()
		return@every
	}

	if (meCurWep.rifle || meCurWep.smg) {
		if (me.shotsFired() < curSettings.int["AIM_AFTER_SHOTS"]) {
			reset()
			return@every
		}
	}

	var currentTarget = target

	val currentAngle = clientState.angle()
	val position = me.position()
	val shouldVisCheck = !(forceAim && curSettings.bool["FORCE_AIM_THROUGH_WALLS"])

	var aB = curSettings["AIM_BONE"]

	if (pressedForceAimKey) {
		aB = curSettings["FORCE_AIM_BONE"]
	}

	val abAsList = aB.stringToIntList()

	val findTargetResList = findTarget(position, currentAngle, aim,
		BONE = if (RANDOM_BONE in abAsList) { destBone = 5 + randInt(0, 3); destBone.toString() } else aB,
		visCheck = shouldVisCheck)
	val bestTarget = findTargetResList.player //Try to find new target
	val bestBone = findTargetResList.closestBone

	if (currentTarget <= 0) { //If target is invalid from last run
		currentTarget = bestTarget //Try to find new target

		if (currentTarget <= 0) { //End if we don't, can't loop because of thread blocking
			reset()
			return@every
		}
		target = currentTarget
	}
	destBone = bestBone

	//Set destination bone for calculating aim
	if (NEAREST_BONE in abAsList) { //Nearest bone check

		if (bestBone != -999) {
			destBone = bestBone
		} else {
			reset()
			return@every
		}
	}

	if (bestTarget <= 0 && !curSettings.bool["HOLD_AIM"] || bestTarget.dead()) {
		reset()
		return@every
	}

	var perfect = false
	if (canPerfect) {
		if (randInt(100+1) <= curSettings.int["PERFECT_AIM_CHANCE"]) {
			perfect = true
		}
	}

	val swapTarget = (bestTarget > 0 && currentTarget != bestTarget) && !curSettings.bool["HOLD_AIM"] && (meCurWep.automatic || curSettings.bool["AUTOMATIC_WEAPONS"])

	if (swapTarget || !currentTarget.canShoot(shouldVisCheck)) {
		reset()
		Thread.sleep(curSettings.int["AIM_TARGET_SWAP_DELAY"].toLong())
	} else {
		val bonePosition = currentTarget.bones(destBone)

		val destinationAngle = getCalculatedAngle(me, bonePosition) //Rename to current angle

		if (!perfect) {
			destinationAngle.finalize(currentAngle, (1.1F - curSettings.float["AIM_SMOOTHNESS"] / 5F)) //10.0 is max smooth value

			val aimSpeed = curSettings.int["AIM_SPEED"]

			val aimSpeedDivisor = if (curSettings.bool["AIM_ADVANCED"]) curSettings.int["AIM_SPEED_DIVISOR"] else 1
			doAim(destinationAngle, currentAngle, aimSpeed, aimSpeedDivisor)
		} else {
			doAim(destinationAngle, currentAngle, 1, 1)
		}
	}
}