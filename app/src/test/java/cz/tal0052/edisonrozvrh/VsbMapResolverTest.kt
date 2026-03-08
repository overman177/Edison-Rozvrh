package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.map.resolveVsbRoomMapInfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VsbMapResolverTest {

    @Test
    fun resolveVsbRoomMapInfo_singleRoom_extractsRoomAndBuilding() {
        val info = resolveVsbRoomMapInfo("POREA425")

        assertNotNull(info)
        assertEquals("POREA425", info!!.roomCode)
        assertEquals("POREA", info.buildingCode)
        assertTrue(info.roomUrl.contains("search=POREA425"))
        assertTrue(info.buildingUrl.contains("search=POREA"))
    }

    @Test
    fun resolveVsbRoomMapInfo_multipleRooms_usesFirstRoom() {
        val info = resolveVsbRoomMapInfo("POREB016, POREB106, POREB308")

        assertNotNull(info)
        assertEquals("POREB016", info!!.roomCode)
        assertEquals("POREB", info.buildingCode)
    }
}
