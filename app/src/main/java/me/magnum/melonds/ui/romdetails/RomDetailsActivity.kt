package me.magnum.melonds.ui.romdetails

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romdetails.ui.RomDetailsScreen
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class RomDetailsActivity : AppCompatActivity() {

    companion object {
        const val KEY_ROM = "rom"
    }

    private val romDetailsViewModel by viewModels<RomDetailsViewModel>()
    private val romRetroAchievementsViewModel by viewModels<RomDetailsRetroAchievementsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val rom by romDetailsViewModel.rom.collectAsState()
            val romConfig by romDetailsViewModel.romConfigUiState.collectAsState()

            val retroAchievementsUiState by romRetroAchievementsViewModel.uiState.collectAsState()

            LaunchedEffect(null) {
                romRetroAchievementsViewModel.viewAchievementEvent.collect {
                    launchViewAchievementIntent(it)
                }
            }

            MelonTheme {
                RomDetailsScreen(
                    rom = rom,
                    romConfigUiState = romConfig,
                    retroAchievementsUiState = retroAchievementsUiState,
                    onNavigateBack = { onNavigateUp() },
                    onLaunchRom = {
                        launchPlayRomIntent(it)
                    },
                    onAddToHomeScreen = {
                        addRomToHomeScreen(it)
                    },
                    onRomConfigUpdate = {
                        romDetailsViewModel.onRomConfigUpdateEvent(it)
                    },
                    onRetroAchievementsLogin = { username, password ->
                        romRetroAchievementsViewModel.login(username, password)
                    },
                    onRetroAchievementsRetryLoad = {
                        romRetroAchievementsViewModel.retryLoadAchievements()
                    },
                    onViewAchievement = {
                        romRetroAchievementsViewModel.viewAchievement(it)
                    }
                )
            }
        }
    }

    private fun launchPlayRomIntent(rom: Rom) {
        val intent = EmulatorActivity.getRomEmulatorActivityIntent(this, rom)
        startActivity(intent)
    }

    private fun launchViewAchievementIntent(achievementUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(achievementUrl)
        }
        startActivity(intent)
    }

    private fun addRomToHomeScreen(rom: Rom) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.shortcut_pin_unsupported, Toast.LENGTH_LONG).show()
            return
        }

        // Use the same LAUNCH_ROM action + KEY_URI that EmulatorActivity already parses, so
        // tapping the home screen icon boots the game directly. We target the activity
        // explicitly (and keep the action) so the pinned shortcut always resolves.
        val launchIntent = Intent("$packageName.LAUNCH_ROM").apply {
            setClass(this@RomDetailsActivity, EmulatorActivity::class.java)
            putExtra(EmulatorActivity.KEY_URI, rom.uri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        lifecycleScope.launch {
            val romIcon = romDetailsViewModel.getRomIcon(rom)
            val shortcutInfo = ShortcutInfoCompat.Builder(this@RomDetailsActivity, rom.uri.toString())
                .setShortLabel(rom.name)
                .setLongLabel(rom.name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(buildShortcutBitmap(romIcon)))
                .setIntent(launchIntent)
                .build()

            val pinned = ShortcutManagerCompat.requestPinShortcut(this@RomDetailsActivity, shortcutInfo, null)
            if (pinned) {
                Toast.makeText(this@RomDetailsActivity, R.string.shortcut_pinned, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildShortcutBitmap(romIcon: RomIcon): Bitmap {
        val iconBitmap = romIcon.bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.logo_splash)
        val shortcutBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(shortcutBitmap)
        canvas.drawRect(Rect(0, 0, shortcutBitmap.width, shortcutBitmap.height), Paint().apply { color = Color.WHITE })
        val iconRect = Rect(77, 77, shortcutBitmap.width - 77, shortcutBitmap.height - 77)
        canvas.drawBitmap(iconBitmap, null, iconRect, Paint().apply { isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR })
        return shortcutBitmap
    }
}