package com.ercompanion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class RetroArchClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 55355,
    private val timeoutMs: Int = 1000
) {
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTED,
        ERROR
    }

    suspend fun readMemory(address: Long, numBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            // Format: READ_CORE_MEMORY <hex_address> <num_bytes>
            val command = "READ_CORE_MEMORY ${address.toString(16)} $numBytes"
            val sendData = command.toByteArray()
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName(host),
                port
            )

            socket.send(sendPacket)

            // Receive response
            val receiveData = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)

            val response = String(receivePacket.data, 0, receivePacket.length)
            socket.close()

            parseMemoryResponse(response, numBytes)
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getStatus(): ConnectionStatus = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            val command = "VERSION"
            val sendData = command.toByteArray()
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName(host),
                port
            )

            socket.send(sendPacket)

            val receiveData = ByteArray(256)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)
            socket.close()

            ConnectionStatus.CONNECTED
        } catch (e: SocketTimeoutException) {
            ConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            ConnectionStatus.ERROR
        }
    }

    private fun parseMemoryResponse(response: String, expectedBytes: Int): ByteArray? {
        // Response format: READ_CORE_MEMORY <hex_address> <hex_byte1> <hex_byte2> ...
        val parts = response.trim().split("\\s+".toRegex())
        if (parts.size < 3 || parts[0] != "READ_CORE_MEMORY") {
            return null
        }

        // Skip command name and address, parse hex bytes
        val hexBytes = parts.drop(2)
        if (hexBytes.size < expectedBytes) {
            return null
        }

        return try {
            hexBytes.take(expectedBytes).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
