package com.anitail.desktop.i18n

import java.util.Locale

fun testStringResolver(overrides: Map<String, String> = emptyMap()): StringResolver {
    val base = mapOf(
        "start_radio" to "Iniciar radio",
        "play_next" to "Reproducir siguiente",
        "add_to_queue" to "Agregar a la cola",
        "add_to_playlist" to "Agregar a playlist",
        "download" to "Descargar",
        "view_artist" to "Ir al artista",
        "share" to "Compartir",
        "play" to "Reproducir",
        "shuffle" to "Aleatorio",
        "subscribe" to "Suscribirse",
        "subscribed" to "Suscrito",
        "remove_download" to "Eliminar descarga",
        "downloading_to_device" to "Descargando",
    ) + overrides
    val bundle = StringsBundle(strings = base, plurals = emptyMap())
    return StringResolver(Locale("es", "ES"), bundle, bundle)
}
