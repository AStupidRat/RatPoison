package rat.poison.settings

import rat.poison.game.Color

/**
 * This gets rid of glow ESP "flicker", and more importantly reduces CPU usage.
 */
var FLICKER_FREE_GLOW = true

/**
 * Will play a hitsound on enemy hit
 */
var ENABLE_HITSOUND = true

/**
 * Volume of the hitsound
 */
var HITSOUND_VOLUME = 0.2

/**
 * Will enable RadarHack
 */
var ENABLE_RADAR = true

/**
 * The color to highlight your team mates.
 */
var TEAM_COLOR = Color(0, 0, 255, 1.0)

/**
 * The color to highlight your enemies.
 */
var ENEMY_COLOR = Color(255, 0, 0, 1.0)

/**
 * The color to highlight the bomb.
 */
var BOMB_COLOR = Color(255, 255, 0, 1.0)

/**
 * The color to highlight weapons.
 */
var WEAPON_COLOR = Color(0, 255, 0, 0.5)

/**
 * The color to highlight grenades.
 */
var GRENADE_COLOR = Color(0, 255, 0, 1.0)
