package org.asistentepro.asistentepro

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhoneToolsTest {
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
