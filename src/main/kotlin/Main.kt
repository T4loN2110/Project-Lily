package com.t4lon.lily

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

import ui.features.chat.ChatScreen
import ui.features.chat.ChatScreenModel
import com.t4lon.lily.lily_impl.*

val appModules = module {
    single { LilyRepository() }
    single { LilyClient(get()) }
    factory { ChatScreenModel(get()) }
}

fun main() = application {
    remember {
        startKoin {
            modules(appModules)
        }
    }
    
    Window(onCloseRequest = ::exitApplication, title = "Project Lily") {
        Navigator(screen = ChatScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}