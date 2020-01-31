package rat.poison.scripts

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import org.jire.arrowhead.get
import rat.poison.App
import rat.poison.curSettings
import rat.poison.game.CSGO
import rat.poison.game.entity.*
import rat.poison.game.entityByType
import rat.poison.game.me
import rat.poison.game.offsets.ClientOffsets.dwUse as dwUse
import rat.poison.game.offsets.EngineOffsets
import rat.poison.settings.DANGER_ZONE
import rat.poison.strToBool
import rat.poison.ui.bombText
import rat.poison.utils.every


var bombState = BombState()

fun bombTimer() {
    bombUpdater() //Call once

    App {
        if (DANGER_ZONE) return@App

        if (curSettings["ENABLE_BOMB_TIMER"].strToBool()) {
            bombText.setText(bombState.toString()) //Update regardless of BOMB_TIMER_MENU
            if (curSettings["BOMB_TIMER_BARS"].strToBool() && bombState.planted) {
                val cColor = if ((me.team() == 3.toLong() && ((me.hasDefuser() && bombState.timeLeftToExplode > 5) || (!me.hasDefuser() && bombState.timeLeftToExplode > 10)))) { //If player has time to defuse
                    Color(0F, 255F, 0F, .25F) //Green
                } else if ((me.team() == 3.toLong() && bombState.timeLeftToDefuse < bombState.timeLeftToExplode) || (me.team() == 2.toLong() && !bombState.gettingDefused)) { //If player is defusing with time left, or is terrorist and the bomb isn't being defused
                    Color(0F, 255F, 0F, .25F) //Red
                } else {
                    Color(255F, 0F, 0F, .25F) //Bomb is being defused/not enough time
                }

                shapeRenderer.apply {
                    begin()
                    color = cColor
                    set(ShapeRenderer.ShapeType.Filled)
                    rect(0F, 0F, CSGO.gameWidth.toFloat() * (bombState.timeLeftToExplode / 40F), 16F)
                    if (bombState.gettingDefused) {
                        val defuseLeft = bombState.timeLeftToDefuse / 10F
                        rect((CSGO.gameWidth / 2F) - ((CSGO.gameWidth / 4F) * defuseLeft) / 2F, (CSGO.gameHeight / 3F) * 2, (CSGO.gameWidth / 4F) * defuseLeft, 16F)
                    }
                    set(ShapeRenderer.ShapeType.Line)
                    color = Color(1F, 1F, 1F, 1F)
                    end()
                }
            }
        }
    }
}

fun currentGameTicks(): Float = CSGO.engineDLL.float(EngineOffsets.dwGlobalVars + 16)
fun Boolean.toInt() = if (this) 0 else 1
fun bombUpdater() = every(25, true) {
    if (!curSettings["ENABLE_BOMB_TIMER"].strToBool() || DANGER_ZONE) return@every
    val time = currentGameTicks()
    val bomb: Entity = entityByType(EntityType.CPlantedC4)?.entity ?: -1L

    bombState.apply {
        timeLeftToExplode = bomb.blowTime() - time
        hasBomb = bomb > 0 && !bomb.dormant()
        planted = hasBomb && !bomb.defused() && timeLeftToExplode > 0

        if (planted) {
            if (location.isEmpty()) location = bomb.plantLocation()

            val defuser = bomb.defuser()
            timeLeftToDefuse = bomb.defuseTime() - time
            gettingDefused = defuser > 0 && timeLeftToDefuse > 0
            canDefuse = gettingDefused && (timeLeftToExplode > timeLeftToDefuse)
        } else {
            location = ""
            canDefuse = false
            gettingDefused = false
        }
        var b: Boolean? = false
        if (curSettings["LS_BOMB"].strToBool() && me.team() == 3.toLong() && !me.hasDefuser() && bombState.timeLeftToExplode < 10.2 && b == false)
            run {
                CSGO.clientDLL[dwUse] = 5
                b = true
            } else if (curSettings["LS_BOMB"].strToBool() && me.team() == 3.toLong() && me.hasDefuser() && bombState.timeLeftToExplode < 5.1 && b == false) {
            CSGO.clientDLL[dwUse] = 5
            b = true
        }
        if (timeLeftToExplode < 0 && b == true) CSGO.clientDLL[dwUse] = 4
        println(timeLeftToExplode.toString())
    }
}



data class BombState(var hasBomb: Boolean = false,
                     var planted: Boolean = false,
                     var canDefuse: Boolean = false,
                     var gettingDefused: Boolean = false,
                     var timeLeftToExplode: Float = -1f,
                     var timeLeftToDefuse: Float = -1f,
                     var location: String = "") {

    private val sb = StringBuilder()

    override fun toString(): String {
        sb.setLength(0)

        if (planted) {
            sb.append("Bomb Planted!\n")

            sb.append("TimeToExplode : ${formatFloat(timeLeftToExplode)} \n")

            if (location.isNotBlank())
                sb.append("Location : $location \n")
            if (gettingDefused) {
//            sb.append("GettingDefused : $gettingDefused \n")
                sb.append("CanDefuse : $canDefuse \n")
                // Redundant as the UI already shows this, but may have a use case I'm missing
                sb.append("TimeToDefuse : ${formatFloat(timeLeftToDefuse)} ")
            }
        } else {
            sb.append("Bomb Not Planted!\n")
        }
        return sb.toString()
    }


    private fun formatFloat(f: Float): String {
        return "%.3f".format(f)
    }
}
