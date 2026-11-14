package org.asistentepro.asistentepro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.Normalizer

class DeviceCommander(private val context: Context, private val engine: AssistantEngine, private val launch: (Intent) -> Unit) {
    fun execute(action: DeviceAction) {
        runCatching {
            when (action) {
                is DeviceAction.Torch -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        engine.error("Concede el permiso de cámara desde la app para usar la linterna."); return
                    }
                    val manager = context.getSystemService(CameraManager::class.java)
                    val camera = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    if (camera == null) { engine.error("Este dispositivo no tiene linterna disponible."); return }
                    val enabled = action.enabled ?: !engine.state.value.torchOn
                    manager.setTorchMode(camera, enabled); engine.torch(enabled)
                    engine.deviceResult(if (enabled) "Linterna encendida." else "Linterna apagada.")
                }
                DeviceAction.Camera -> { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); engine.deviceResult("Abrí la cámara.") }
                DeviceAction.Wifi -> { launch(Intent(Settings.ACTION_WIFI_SETTINGS)); engine.deviceResult("Abrí los ajustes de Wi-Fi.") }
                DeviceAction.Sms -> { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))); engine.deviceResult("Abrí Mensajes. Elige el destinatario y confirma el envío.") }
                DeviceAction.Help -> engine.deviceResult("Soy Xylon. Puedes decir «Xylon, abre YouTube», consultar la batería, cambiar el volumen, usar la linterna o crear alarmas. Activa Manos libres en Ajustes para llamarme fuera de la app, sin registrar tu voz. Para preguntas generales elige y configura tu proveedor de IA.")
                is DeviceAction.Volume -> {
                    val audio = context.getSystemService(AudioManager::class.java)
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (action.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    engine.deviceResult("Volumen multimedia: ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} de ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}.")
                }
                is DeviceAction.OpenApp -> {
                    val pm = context.packageManager
                    fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").replace(" ", "")
                    val target = normalized(action.name)
                    val aliases = mapOf("youtube" to "com.google.android.youtube", "spotify" to "com.spotify.music", "whatsapp" to "com.whatsapp", "netflix" to "com.netflix.mediaclient")
                    val activities = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    val activity = activities.firstOrNull { it.activityInfo.packageName == aliases[target] }
                        ?: activities.firstOrNull { normalized(it.loadLabel(pm).toString()) == target }
                        ?: activities.firstOrNull { normalized(it.loadLabel(pm).toString()).contains(target) }
                    if (activity == null) { engine.error("No encuentro ${action.name} instalada. Instálala para poder abrirla."); return }
                    launch(Intent.makeMainActivity(ComponentName(activity.activityInfo.packageName, activity.activityInfo.name)))
                    engine.deviceResult("Abrí ${activity.loadLabel(pm)}.")
                }
                is DeviceAction.Alarm -> {
                    launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, action.hour).putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "Xylon").putExtra(AlarmClock.EXTRA_SKIP_UI, false))
                    engine.deviceResult("Abrí Reloj. Revisa y confirma la alarma.")
                }
                DeviceAction.Battery -> {
                    val level = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level in 0..100) engine.deviceResult("Tu batería está al $level %.") else engine.error("Android no pudo informar el nivel de batería.")
                }
                is DeviceAction.Timer -> {
                    launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "Xylon").putExtra(AlarmClock.EXTRA_SKIP_UI, false))
                    engine.deviceResult("Abrí Reloj con un temporizador de ${action.seconds} segundos. Confírmalo allí.")
                }
            }
        }.onFailure { engine.error("Android no pudo abrir esta función. Revisa los permisos, desbloquea el teléfono y comprueba que la app esté instalada.") }
    }
}
