package com.anitail.music.cast

import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

/**
 * Actividad expandida personalizada para el control de Cast.
 * Definirla nos permite declararla en el manifest con parentActivity y evita
 * fallos en TaskStackBuilder cuando se usa la clase genérica del framework.
 */
class CastExpandedActivity : ExpandedControllerActivity()
