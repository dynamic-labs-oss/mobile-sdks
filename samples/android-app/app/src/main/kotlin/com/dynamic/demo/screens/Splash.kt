package com.dynamic.demo.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dynamic.demo.R

@Composable
fun SplashView() {
    // Full-screen login_background.png (pulled from the Dynamic Flutter SDK
    // example app's assets/splash.png) — matches its own SplashScreen,
    // which is JUST this image; the progress indicator is this demo's own
    // addition on top of it.
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
