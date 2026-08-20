package com.boomersolitaire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boomersolitaire.app.data.SaveRepository
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.data.SettingsRepository
import com.boomersolitaire.app.data.StatsDatabase
import com.boomersolitaire.app.game.GameViewModel
import com.boomersolitaire.app.ui.screens.GameScreen
import com.boomersolitaire.app.ui.screens.HomeScreen
import com.boomersolitaire.app.ui.screens.SettingsScreen
import com.boomersolitaire.app.ui.screens.StatsScreen
import com.boomersolitaire.app.ui.theme.BoomerTheme

class MainActivity : ComponentActivity() {

    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val saveRepo by lazy { SaveRepository(applicationContext) }
    private val statsDb by lazy { StatsDatabase.get(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(settingsRepo, saveRepo, statsDb)
        }
    }
}

@Composable
private fun App(
    settingsRepo: SettingsRepository,
    saveRepo: SaveRepository,
    statsDb: StatsDatabase,
) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val vm: GameViewModel = viewModel(
        factory = GameViewModel.Factory(settingsRepo, saveRepo, statsDb.dao()),
    )

    BoomerTheme(theme = settings.theme) {
        val nav = rememberNavController()
        val hasSavedGame by saveRepo.hasSavedGame.collectAsStateWithLifecycle(initialValue = false)
        val dayStreak by settingsRepo.dayStreak.collectAsStateWithLifecycle(initialValue = 0L)

        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    hasSavedGame = hasSavedGame,
                    dayStreak = dayStreak,
                    onPlay = { nav.navigate("game") { launchSingleTop = true } },
                    onNewGame = {
                        vm.newGame()
                        nav.navigate("game") { launchSingleTop = true }
                    },
                    onScores = { nav.navigate("scores") { launchSingleTop = true } },
                    onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                )
            }
            composable("game") {
                GameScreen(
                    vm = vm,
                    onBackToMenu = { nav.popBackStack("home", inclusive = false) },
                )
            }
            composable("scores") {
                StatsScreen(dao = statsDb.dao(), onBack = { nav.popBackStack("home", inclusive = false) })
            }
            composable("settings") {
                SettingsScreen(repo = settingsRepo, onBack = { nav.popBackStack("home", inclusive = false) })
            }
        }
    }
}
