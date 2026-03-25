package com.ercompanion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class RetroArchClient(
    var host: String = "127.0.0.1",
    var port: Int = 55355,
    private val timeoutMs: Int = 2000
) {
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTED,
        ERROR
    }

    // Debug log — last N entries
    val debugLog = ArrayDeque<String>(50)

    private fun log(msg: String) {
        if (debugLog.size >= 50) debugLog.removeFirst()
        debugLog.addLast(msg)
        android.util.Log.d("RetroArchClient", msg)
    }

    private suspend fun sendCommand(command: String): String? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            log("→ SEND [$host:$port] $command")

            val sendData = command.toByteArray()
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName(host),
                port
            )
            socket.send(sendPacket)

            val receiveData = ByteArray(65536)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)
            socket.close()

            val response = String(receivePacket.data, 0, receivePacket.length).trim()
            log("← RECV $response")
            response
        } catch (e: SocketTimeoutException) {
            log("✗ TIMEOUT after ${timeoutMs}ms")
            null
        } catch (e: Exception) {
            log("✗ ERROR ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun readMemory(address: Long, numBytes: Int): ByteArray? {
        val cmd = "READ_CORE_MEMORY ${address.toString(16)} $numBytes"
        val response = sendCommand(cmd) ?: return null
        return parseMemoryResponse(response, numBytes)
    }

    suspend fun getStatus(): ConnectionStatus {
        // Try GET_STATUS first (more common), fall back to VERSION
        var response = sendCommand("GET_STATUS")
        if (response != null) return ConnectionStatus.CONNECTED

        response = sendCommand("VERSION")
        if (response != null) return ConnectionStatus.CONNECTED

        // Last resort: try a harmless memory read
        response = sendCommand("READ_CORE_MEMORY 0 1")
        return if (response != null) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
    }

    fun getDebugLog(): List<String> = debugLog.toList()

    private fun parseMemoryResponse(response: String, expectedBytes: Int): ByteArray? {
        val parts = response.trim().split("\\s+".toRegex())
        if (parts.size < 3 || parts[0] != "READ_CORE_MEMORY") {
            log("✗ PARSE FAIL: unexpected format: $response")
            return null
        }
        // Check for error response
        if (parts[2] == "-1" || parts[2].startsWith("error")) {
            log("✗ READ ERROR from RetroArch: $response")
            return null
        }
        val hexBytes = parts.drop(2)
        return try {
            hexBytes.take(expectedBytes).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            log("✗ PARSE FAIL: bad hex in response")
            null
        }
    }
}
