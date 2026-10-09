package com.cinemawatch.flow

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM frames over the local LAN; no clear-text identifiers or shared key on the wire. */
internal object FlowCipher {
    fun newKey() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun key(hex: String): SecretKeySpec { require(Regex("[0-9a-fA-F]{64}").matches(hex)); return SecretKeySpec(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),"AES") }
    fun encrypt(text: String, hex: String): ByteArray {
        val nonce=ByteArray(12).also { SecureRandom().nextBytes(it) }; val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(hex),GCMParameterSpec(128,nonce));return nonce+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(bytes: ByteArray, hex: String): String {
        require(bytes.size in 28..65536);val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(hex),GCMParameterSpec(128,bytes.copyOfRange(0,12)));return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }
}
internal object FlowWire {
    const val PORT=8765
    fun privateHost(host: String): Boolean {
        val parts=host.split('.').map { it.toIntOrNull() ?: return false }
        if(parts.size!=4 || parts.any { it !in 0..255 })return false
        return parts[0]==10 || parts[0]==192 && parts[1]==168 || parts[0]==172 && parts[1] in 16..31 || parts==listOf(127,0,0,1)
    }
    fun write(socket: Socket, body: JSONObject, key: String) { val data=FlowCipher.encrypt(body.toString(),key);require(data.size<=65536);DataOutputStream(socket.getOutputStream()).apply { writeInt(data.size);write(data);flush() } }
    fun read(socket: Socket,key:String): JSONObject { val input=DataInputStream(socket.getInputStream());val size=input.readInt();require(size in 28..65536);val bytes=ByteArray(size);input.readFully(bytes);return JSONObject(FlowCipher.decrypt(bytes,key)) }
    fun exchange(host:String,body:JSONObject,key:String): JSONObject {
        require(privateHost(host));return Socket().use { socket -> socket.connect(InetSocketAddress(host,PORT),2000);socket.soTimeout=2500;write(socket,body,key);read(socket,key) }
    }
}
internal class FlowServer(private val key:String,private val handler:(JSONObject)->JSONObject): AutoCloseable {
    private val server=ServerSocket().apply { reuseAddress=true;bind(InetSocketAddress(FlowWire.PORT));soTimeout=1000 }
    @Volatile private var running=true
    fun serve() {
        while(running) {
            try { server.accept().use { socket -> socket.soTimeout=2000;runCatching { FlowWire.write(socket,handler(FlowWire.read(socket,key)),key) } } }
            catch (_: Exception) { if(!running)break }
        }
    }
    override fun close() { running=false;server.close() }
}
