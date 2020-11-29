package rat.poison.overlay

import com.badlogic.gdx.InputProcessor

//rewrite to events
class KeyProcessor: InputProcessor {
    var needKeyPress = false
    lateinit var callBack : (Int, String) -> Unit

    override fun keyDown(keycode: Int): Boolean {
        if (needKeyPress) {
            callBack(keycode, "button")
            needKeyPress = false
        }
        return true
    }

    fun removeCallback() {
        callBack = { _, _ -> }
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (needKeyPress) {
            callBack(button, "mouse")
            needKeyPress = false
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean { return true }
    override fun keyTyped(character: Char): Boolean { return true }
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { return true }
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean { return true }
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean { return true }
    override fun scrolled(amountX: Float, amountY: Float): Boolean { return true }
}