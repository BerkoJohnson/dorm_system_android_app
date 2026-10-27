package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage

@Composable
fun TenantLogo(
    logoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val fallback = painterResource(id = com.example.R.drawable.edenest_icon)
    if (logoUrl.isBlank()) {
        Image(
            painter = fallback,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        AsyncImage(
            model = logoUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            placeholder = fallback,
            error = fallback
        )
    }
}
