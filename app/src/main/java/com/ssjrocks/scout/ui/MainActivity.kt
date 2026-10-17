package com.ssjrocks.scout.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        com.ssjrocks.scout.net.Updater.foreground = true
    }

    override fun onStop() {
        com.ssjrocks.scout.net.Updater.foreground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            ACTION_INSTALL_UPDATE -> vm.checkForUpdate(installIfFound = true)
            ACTION_SEARCH -> if (vm.screens.lastOrNull() != Screen.Search) vm.push(Screen.Search)
            ACTION_OPEN_STORY -> {
                val url = i.getStringExtra(EXTRA_URL) ?: return
                i.getStringExtra(EXTRA_ID)?.let { id -> vm.repo.setState(id) { it.copy(read = System.currentTimeMillis()) } }
                val s = vm.settings.value
                val dark = when (s.theme) {
                    "light" -> false
                    "dark", "black" -> true
                    else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                }
                if (s.openIn == "reader") vm.push(Screen.Reader(i.getStringExtra(EXTRA_ID), url)) else Links.open(this, url, s, dark)
            }
            Intent.ACTION_SEND -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val url = Regex("https?://\\S+").find(text)?.value ?: text.trim()
                pendingAdd.value = url
                tab.value = 1
            }
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.ssjrocks.scout.SEARCH"
        const val ACTION_INSTALL_UPDATE = "com.ssjrocks.scout.INSTALL_UPDATE"
        const val ACTION_OPEN_STORY = "com.ssjrocks.scout.OPEN_STORY"
        const val EXTRA_URL = "url"
        const val EXTRA_ID = "id"

        /** A link shared into Scout, waiting for the Add source dialog. */
        val pendingAdd = MutableStateFlow<String?>(null)
        val tab = MutableStateFlow(0)
    }
}

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Discover", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("Following", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("Saved", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Tab("Filters", Icons.Outlined.Tune, Icons.Filled.Tune),
)

@Composable
fun App(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    ScoutTheme(s) {
        val ctx = LocalContext.current
        val imagesOn = when (s.images) {
            "never" -> false
            "wifi" -> !Links.isMetered(ctx)
            else -> true
        }
        CompositionLocalProvider(LocalImagesOn provides imagesOn) {
            val snackHost = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                vm.snacks.collect { sn ->
                    snackHost.currentSnackbarData?.dismiss()
                    val r = snackHost.showSnackbar(sn.message, actionLabel = if (sn.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) sn.undo?.invoke()
                }
            }
            val tab by MainActivity.tab.collectAsStateWithLifecycle()
            val top = vm.screens.lastOrNull()
            BackHandler(enabled = top == null && tab != 0) { MainActivity.tab.value = 0 }
            BackHandler(enabled = top != null) { vm.pop() }

            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackHost) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { MainActivity.tab.value = i },
                                    icon = { Icon(if (tab == i) t.selectedIcon else t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    when (tab) {
                        0 -> FeedScreen(vm, padding)
                        1 -> FollowingScreen(vm, padding)
                        2 -> SavedScreen(vm, padding)
                        else -> FiltersScreen(vm, padding)
                    }
                }
                vm.screens.forEach { screen ->
                    AnimatedVisibility(
                        visibleState = remember(screen) { MutableTransitionState(false).apply { targetState = true } },
                        enter = slideInHorizontally { it / 4 } + fadeIn(),
                        exit = slideOutHorizontally { it / 4 } + fadeOut(),
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            when (screen) {
                                Screen.Search -> SearchScreen(vm)
                                Screen.Settings -> SettingsScreen(vm)
                                is Screen.Reader -> ReaderScreen(vm, screen)
                                is Screen.Story -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
