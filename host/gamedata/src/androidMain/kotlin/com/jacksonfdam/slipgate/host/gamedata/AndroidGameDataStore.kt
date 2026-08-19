package com.jacksonfdam.slipgate.host.gamedata

import android.content.Context
import java.io.File

private const val FOLDER = "gamedata"

/**
 * Stores game data in the app's private files directory.
 *
 * Private rather than external storage: the files belong to this app, no other app has a reason to
 * read them, and nothing else on the device should be able to swap a validated IWAD for something
 * else after the fact. Uninstalling the app takes them with it, which is the behaviour a player
 * expects of data they imported into it.
 */
public fun androidGameDataStore(context: Context): GameDataStore = FileGameDataStore(File(context.filesDir, FOLDER))
