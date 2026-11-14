package org.asistentepro.asistentepro

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhoneToolsTest {
    @Test fun systemActionsPreserveValuesAndRejectUnrequestedOrInvalidControls() {
        assertEquals(DeviceAction.Navigate(SystemNavigation.Home), PhoneTools.decode("control_phone", JSONObject("""{"action":"navigate","destination":"home"}""")))
        assertEquals(DeviceAction.Brightness(42), PhoneTools.decode("control_phone", JSONObject("""{"action":"brightness","percent":42}""")))
        assertEquals(DeviceAction.AutoRotate(false), PhoneTools.decode("control_phone", JSONObject("""{"action":"auto_rotate","enabled":false}""")))
        assertEquals(DeviceAction.VolumeLevel(0), PhoneTools.decode("control_phone", JSONObject("""{"action":"volume_level","percent":0}""")))
        assertEquals(DeviceAction.CurrentTime, PhoneTools.decode("control_phone", JSONObject("""{"action":"current_time"}""")))
        listOf("""{"action":"brightness","percent":101}""", """{"action":"volume_level","percent":-1}""",
            """{"action":"navigate","destination":"factory_reset"}""", """{"action":"auto_rotate","enabled":"yes"}""",
            """{"action":"open_settings","panel":"grant_every_permission"}""").forEach { args ->
            try { PhoneTools.decode("control_phone", JSONObject(args)); fail("Invalid control accepted") }
            catch (_: AssistantException) { }
        }
    }
    @Test fun arbitraryApplicationNamesAndValidAlarmValuesArePreserved() {
        assertEquals(DeviceAction.OpenApp("Una app cualquiera"), PhoneTools.decode("control_phone", JSONObject("""{"action":"open_app","app_name":"Una app cualquiera"}""")))
        assertEquals(DeviceAction.Alarm(23, 45), PhoneTools.decode("control_phone", JSONObject("""{"action":"alarm","hour":23,"minute":45}""")))
    }
    @Test fun malformedOrUnavailableActionsCannotExecute() {
        listOf("""{"action":"delete_everything"}""", """{"action":"alarm","hour":24,"minute":0}""",
            """{"action":"timer","seconds":1.5}""", """{"action":"open_app","app_name":""}""",
            """{"action":"torch","enabled":"true"}""").forEach { args ->
            try { PhoneTools.decode("control_phone", JSONObject(args)); fail("Should reject $args") }
            catch (_: AssistantException) { }
        }
    }
}
