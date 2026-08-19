package com.jacksonfdam.slipgate.host.gamedata

/** Something in a compressed stream did not make sense. */
public class InflateException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
