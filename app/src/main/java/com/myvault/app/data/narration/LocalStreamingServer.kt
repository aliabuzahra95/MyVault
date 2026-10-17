package com.myvault.app.data.narration

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

@Singleton
class LocalStreamingServer @Inject constructor() {
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        serverSocket = ServerSocket(0)
        port = serverSocket!!.localPort
        isRunning = true
        thread(isDaemon = true, name = "LocalStreamingServer") {
            while (isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                } catch (e: Exception) {
                    if (isRunning) Log.e("StreamingServer", "Accept error", e)
                }
            }
        }
        Log.d("StreamingServer", "Started on port $port")
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        port = 0
    }

    private fun handleClient(client: Socket) {
        thread(isDaemon = true, name = "StreamingClient") {
            try {
                val input = client.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@thread
                if (!requestLine.startsWith("GET ")) return@thread

                val path = requestLine.split(" ")[1]
                val file = File(path)

                // Wait briefly if file is just being created
                for (i in 0 until 50) {
                    if (file.exists() && file.length() > 0) break
                    Thread.sleep(100)
                }

                if (!file.exists()) {
                    client.getOutputStream().write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                    client.close()
                    return@thread
                }

                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nConnection: close\r\n\r\n".toByteArray())

                val fis = FileInputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int

                var idleTime = 0
                while (isRunning) {
                    bytesRead = fis.read(buffer)
                    if (bytesRead > 0) {
                        client.getOutputStream().write(buffer, 0, bytesRead)
                        idleTime = 0
                    } else {
                        Thread.sleep(100)
                        idleTime += 100
                        if (File(file.absolutePath + ".done").exists()) break
                        if (idleTime > 60000) break // 60s timeout waiting for generation
                    }
                }
            } catch (e: Exception) {
                // Ignore client disconnects
            } finally {
                client.close()
            }
        }
    }
}
