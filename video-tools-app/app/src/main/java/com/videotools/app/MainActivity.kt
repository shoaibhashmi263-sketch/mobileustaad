package com.videotools.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

enum class Screen { HOME, COMPRESSOR, CONVERTER, TRIMMER, STATUS }

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Coil with a video-frame decoder so status thumbnails work for videos too.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(VideoFrameDecoder.Factory()) }
                .build()
        )

        val prefs = getSharedPreferences("vt_prefs", Context.MODE_PRIVATE)

        setContent {
            var lang by remember {
                mutableStateOf(
                    if (prefs.getString("lang", "en") == "ur") AppLanguage.UR else AppLanguage.EN
                )
            }
            val s = if (lang == AppLanguage.UR) StringsUr else StringsEn
            var screen by remember { mutableStateOf(Screen.HOME) }

            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF00796B),
                    secondary = Color(0xFF00897B)
                )
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (screen) {
                                        Screen.HOME -> "Video Tools"
                                        Screen.COMPRESSOR -> s.compressor
                                        Screen.CONVERTER -> s.converter
                                        Screen.TRIMMER -> s.trimmer
                                        Screen.STATUS -> s.statusSaver
                                    }
                                )
                            },
                            navigationIcon = {
                                if (screen != Screen.HOME) {
                                    IconButton(onClick = { screen = Screen.HOME }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null
                                        )
                                    }
                                }
                            },
                            actions = {
                                TextButton(onClick = {
                                    val newLang =
                                        if (lang == AppLanguage.EN) AppLanguage.UR else AppLanguage.EN
                                    lang = newLang
                                    prefs.edit()
                                        .putString("lang", if (newLang == AppLanguage.UR) "ur" else "en")
                                        .apply()
                                }) {
                                    Text(if (lang == AppLanguage.EN) "اردو" else "EN")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(s) { screen = it }
                            Screen.COMPRESSOR -> CompressorScreen(s)
                            Screen.CONVERTER -> ConverterScreen(s)
                            Screen.TRIMMER -> TrimmerScreen(s)
                            Screen.STATUS -> StatusSaverScreen(s)
                        }
                    }
                }
            }
        }
    }
}
