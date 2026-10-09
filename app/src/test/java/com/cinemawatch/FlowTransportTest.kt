package com.cinemawatch

import com.cinemawatch.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=android.app.Application::class)
class FlowTransportTest {
    @Test fun authenticatedEncryptionRejectsTamperingAndWrongKeys() {
        val key=FlowCipher.newKey();val plain="authorized tag AA:BB:CC:DD:EE:01"
        val bytes=FlowCipher.encrypt(plain,key)
        assertEquals(plain,FlowCipher.decrypt(bytes,key))
        assertFalse(bytes.contentEquals(FlowCipher.encrypt(plain,key)))
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { FlowCipher.decrypt(bytes,key) }
        assertThrows(Exception::class.java) { FlowCipher.decrypt(FlowCipher.encrypt(plain,key),FlowCipher.newKey()) }
        assertThrows(IllegalArgumentException::class.java) { FlowCipher.decrypt(ByteArray(65537),key) }
    }
    @Test fun allowsOnlyNumericLocalAddresses() {
        listOf("10.0.0.1","192.168.1.2","172.16.0.1","172.31.255.2","127.0.0.1").forEach { assertTrue(FlowWire.privateHost(it)) }
        listOf("github.com","8.8.8.8","172.32.0.1","192.168.1.999","localhost","10.0.0.1/path").forEach { assertFalse(FlowWire.privateHost(it)) }
    }
    @Test fun reportsExcludeAddressesPairingCodeAndSessionIdentity() {
        val config=FlowConfig("session-secret","Cinema",mapOf("A" to "Lobby","B" to "Corridor"),listOf(FlowTarget("session-id","Owned beacon","AA:BB:CC:DD:EE:01")),"Lobby → Corridor",100000)
        val snapshot=FlowSnapshot(nodes=listOf(FlowNode("node-id","Lobby phone","A",0,10000,true,1)),presence=listOf(FlowPresence("session-id","Owned beacon","A",10,2)))
        val report=reportJson(config,snapshot).toString()
        listOf("AA:BB:CC:DD:EE:01","session-secret","session-id","node-id","address","pairing").forEach { assertFalse(report.contains(it)) }
        assertEquals("Cinema",JSONObject(report).getString("cinema"))
    }
}
