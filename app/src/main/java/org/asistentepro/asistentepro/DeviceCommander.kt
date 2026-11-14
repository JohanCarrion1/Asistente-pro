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
import android.view.KeyEvent
import kotlin.math.roundToInt

class DeviceCommander(private val context: Context, private val engine: AssistantEngine, private val launch: (Intent) -> Unit) {
    fun execute(action: DeviceAction) {
        runCatching {
            when (action) {
                DeviceAction.CurrentTime -> engine.deviceResult("Son las ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())}. Zona horaria: ${java.util.TimeZone.getDefault().id}.")
                DeviceAction.CurrentDate -> engine.deviceResult(DeviceClock.date())
                is DeviceAction.Navigate -> {
                    val service = XylonControlService.active
                    if (service == null) { engine.error("Activa Control del sistema en Ajustes de Xylon → Accesibilidad para usar esta acción."); return }
                    if (action.destination != SystemNavigation.Lock && context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { engine.error("Desbloquea el teléfono para usar esta acción."); return }
                    if (service.navigate(action.destination)) engine.deviceResult("Acción del sistema: ${action.destination.title}.")
                    else engine.error("Android no permitió esa acción en la pantalla actual.")
                }
                is DeviceAction.Brightness -> {
                    if (!requireSettingsPermission()) return
                    require(action.percent in 0..100)
                    check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                    check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (action.percent * 255 / 100f).roundToInt().coerceAtLeast(1)))
                    engine.deviceResult("Brillo ajustado al ${action.percent} %.")
                }
                is DeviceAction.AutoRotate -> {
                    if (!requireSettingsPermission()) return
                    check(Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (action.enabled) 1 else 0))
                    engine.deviceResult(if (action.enabled) "Rotación automática activada." else "Rotación automática desactivada.")
                }
                is DeviceAction.VolumeLevel -> {
                    require(action.percent in 0..100)
                    val audio = context.getSystemService(AudioManager::class.java)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * action.percent / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
                    engine.deviceResult("Volumen multimedia: ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} de ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}.")
                }
                is DeviceAction.Media -> {
                    val code = when (action.command) { MediaCommand.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; MediaCommand.Next -> KeyEvent.KEYCODE_MEDIA_NEXT; MediaCommand.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS }
                    val audio = context.getSystemService(AudioManager::class.java)
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                    engine.deviceResult("Envié el control multimedia al reproductor activo.")
                }
                is DeviceAction.OpenSettings -> {
                    val panel = when (action.panel) { SettingsPanel.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS; SettingsPanel.Location -> Settings.ACTION_LOCATION_SOURCE_SETTINGS; SettingsPanel.Internet -> Settings.ACTION_WIRELESS_SETTINGS; SettingsPanel.Sound -> Settings.ACTION_SOUND_SETTINGS; SettingsPanel.Display -> Settings.ACTION_DISPLAY_SETTINGS }
                    launch(Intent(panel)); engine.deviceResult("Abrí los ajustes del sistema. Confirma allí el cambio que Android requiera.")
                }
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
    private fun requireSettingsPermission(): Boolean {
        if (Settings.System.canWrite(context)) return true
        engine.error("Activa Modificar ajustes del sistema desde Ajustes de Xylon para cambiar el brillo o la rotación.")
        return false
    }
}
