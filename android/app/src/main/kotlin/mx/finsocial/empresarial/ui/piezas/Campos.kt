package mx.finsocial.empresarial.ui.piezas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.ui.tema.Finso

/**
 * Campo de texto de Finso: etiqueta arriba en el color de marca y caja en
 * píldora con borde sutil que se ilumina al enfocar.
 */
@Composable
fun CampoFinso(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    acciones: KeyboardActions = KeyboardActions.Default,
    tipoAutollenado: ContentType? = null,
    contrasena: Boolean = false,
    transformacion: VisualTransformation = VisualTransformation.None,
    habilitado: Boolean = true,
    conError: Boolean = false,
) {
    val c = Finso.colores
    var verContrasena by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.titleSmall, color = c.acento)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = valor,
            onValueChange = alCambiar,
            singleLine = true,
            enabled = habilitado,
            isError = conError,
            placeholder = if (ayuda != null) {
                { Text(ayuda, style = MaterialTheme.typography.bodyLarge, color = c.secundario) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = teclado,
            keyboardActions = acciones,
            visualTransformation = when {
                contrasena && !verContrasena -> PasswordVisualTransformation()
                else -> transformacion
            },
            trailingIcon = if (contrasena) {
                {
                    IconButton(onClick = { verContrasena = !verContrasena }) {
                        Icon(
                            if (verContrasena) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (verContrasena) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = c.secundario,
                        )
                    }
                }
            } else {
                null
            },
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.acento,
                unfocusedBorderColor = c.borde.copy(alpha = .6f),
                focusedContainerColor = if (c.esOscuro) c.elevado.copy(alpha = .6f) else c.tarjeta,
                unfocusedContainerColor = if (c.esOscuro) c.elevado.copy(alpha = .4f) else c.tarjeta,
                cursorColor = c.acento,
                errorBorderColor = c.peligro,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (tipoAutollenado != null) contentType = tipoAutollenado },
        )
    }
}
