package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.ui.graphics.ImageBitmap
import com.jacksonfdam.slipgate.host.graphics.core.ArgbImage

/**
 * Uploads CPU-resolved pixels into a platform image. Only the classic backend needs this; the
 * shader backends keep their pixels on the GPU.
 */
internal expect fun ArgbImage.toImageBitmap(): ImageBitmap
