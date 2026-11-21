package mx.finsocial.empresarial.ui.piezas

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Vibraciones de la app (especificación §4): ligera al copiar y al elegir,
 * media al confirmar con éxito y fuerte en error de SPEI. Usa las del sistema
 * para que se sientan como el resto del teléfono.
 */
object Hapticos {
    fun ligero(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun exito(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun error(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}
