package com.jacksonfdam.slipgate.ui.gate

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jacksonfdam.slipgate.host.graphics.core.ArgbImage

internal actual fun ArgbImage.toImageBitmap(): ImageBitmap =
    Bitmap
        .createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
