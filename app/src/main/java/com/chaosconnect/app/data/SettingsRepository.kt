package com.chaosconnect.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferences: DataStore<Preferences> by preferencesDataStore("appearance")

enum class DarkMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class Palette(val label: String) { TonalSpot("柔和"), Neutral("中性"), Vibrant("鲜明"), Expressive("生动"), Rainbow("彩虹"), FruitSalad("缤纷"), Monochrome("单色"), Fidelity("保真"), Content("内容") }

data class AppSettings(
    val useMiuix: Boolean = true,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val instantDock: Boolean = true,
    val haptics: Boolean = true,
    val seedColor: Long = 0xFF2B82E9,
    val blur: Boolean = true,
    val floatingDock: Boolean = true,
    val palette: Palette = Palette.Expressive,
    val spec2025: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.preferences
    private object Keys {
        val miuix = booleanPreferencesKey("miuix")
        val dark = stringPreferencesKey("dark")
        val dynamic = booleanPreferencesKey("dynamic")
        val black = booleanPreferencesKey("black")
        val instant = booleanPreferencesKey("instant_dock")
        val haptics = booleanPreferencesKey("haptics")
        val seed = longPreferencesKey("seed")
        val blur = booleanPreferencesKey("blur")
        val floating = booleanPreferencesKey("floating")
        val palette = stringPreferencesKey("palette")
        val spec = booleanPreferencesKey("spec2025")
    }
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        AppSettings(
            useMiuix = p[Keys.miuix] ?: true,
            darkMode = DarkMode.entries.find { it.name == p[Keys.dark] } ?: DarkMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            pureBlack = p[Keys.black] ?: false,
            instantDock = p[Keys.instant] ?: true,
            haptics = p[Keys.haptics] ?: true,
            seedColor = p[Keys.seed] ?: 0xFF2B82E9,
            blur = p[Keys.blur] ?: true,
            floatingDock = p[Keys.floating] ?: true,
            palette = Palette.entries.find { it.name == p[Keys.palette] } ?: Palette.Expressive,
            spec2025 = p[Keys.spec] ?: true,
        )
    }
    suspend fun update(value: AppSettings) = store.edit {
        it[Keys.miuix] = value.useMiuix
        it[Keys.dark] = value.darkMode.name
        it[Keys.dynamic] = value.dynamicColor
        it[Keys.black] = value.pureBlack
        it[Keys.instant] = value.instantDock
        it[Keys.haptics] = value.haptics
        it[Keys.seed] = value.seedColor
        it[Keys.blur] = value.blur
        it[Keys.floating] = value.floatingDock
        it[Keys.palette] = value.palette.name
        it[Keys.spec] = value.spec2025
    }
}
