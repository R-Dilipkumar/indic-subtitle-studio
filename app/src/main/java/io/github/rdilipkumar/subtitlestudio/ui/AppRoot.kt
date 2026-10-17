package io.github.rdilipkumar.subtitlestudio.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorScreen
import io.github.rdilipkumar.subtitlestudio.ui.home.HomeScreen
import io.github.rdilipkumar.subtitlestudio.ui.home.HomeViewModel
import io.github.rdilipkumar.subtitlestudio.ui.packs.PacksScreen
import io.github.rdilipkumar.subtitlestudio.ui.setup.ProcessingScreen
import io.github.rdilipkumar.subtitlestudio.ui.setup.SetupScreen

/**
 * Screens: home → setup ("what should we do?") → processing → editor (the sandbox).
 * Routes are plain strings so they survive rotation and process restarts.
 */
@Composable
fun AppRoot(sharedVideo: Uri?, onSharedVideoTaken: () -> Unit) {
  var route by rememberSaveable { mutableStateOf("home") }
  val home: HomeViewModel = viewModel()

  LaunchedEffect(sharedVideo) {
    if (sharedVideo != null) {
      onSharedVideoTaken()
      route = "home"
      home.import(sharedVideo) { id -> route = "setup/$id" }
    }
  }

  val parts = route.split('/')
  BackHandler(enabled = route != "home") {
    route = when (parts[0]) {
      "processing" -> "home"
      else -> "home"
    }
  }

  AnimatedContent(targetState = route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screens") { r ->
    val p = r.split('/')
    ScreenScope(r) {
    when (p[0]) {
      "setup" -> SetupScreen(
        projectId = p[1],
        onBack = { route = "home" },
        onStarted = { route = "processing/${p[1]}" },
        onSkip = { route = "editor/${p[1]}" },
        onOpenPacks = { route = "packs" },
      )
      "processing" -> ProcessingScreen(
        projectId = p[1],
        onDone = { route = "editor/${p[1]}" },
        onBack = { route = "home" },
        onRetry = { route = "setup/${p[1]}" },
      )
      "editor" -> EditorScreen(projectId = p[1], onBack = { route = "home" }, onRedo = { route = "setup/${p[1]}" })
      "packs" -> PacksScreen(onBack = { route = "home" })
      else -> HomeScreen(
        vm = home,
        onOpen = { meta -> route = if (meta.status == io.github.rdilipkumar.subtitlestudio.core.model.ProjectStatus.READY) "editor/${meta.id}" else "setup/${meta.id}" },
        onCreated = { id -> route = "setup/$id" },
        onOpenPacks = { route = "packs" },
        onOpenRunning = { id -> route = "processing/$id" },
      )
    }
    }
  }
}

/**
 * Gives each screen visit its own ViewModels, cleared when the screen goes away. Without
 * this, reopening the editor after re-running the AI would show the old captions, and the
 * video player would live on after leaving.
 */
@Composable
private fun ScreenScope(key: String, content: @Composable () -> Unit) {
  val app = LocalContext.current.applicationContext as Application
  val owner = remember(key) {
    object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
      override val viewModelStore = ViewModelStore()
      override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(app)
      override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras().apply { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app) }
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
