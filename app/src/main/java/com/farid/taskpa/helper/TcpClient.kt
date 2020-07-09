package com.farid.taskpa.helper

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*


class TcpClient : Observable {
    private var address: String? = null
    private var port: Int? = null
    private val timeout = 2000

    var status: TcpClientState =
        TcpClientState.DISCONNECTED

    var bufferOut: PrintWriter? = null
    var bufferIn: BufferedReader? = null
    var socket: Socket? = null

    constructor() {}

    constructor(address: String?, port: Int) {
        this.address = address
        this.port = port
    }

    fun fireEvent(event: TcpEvent?) {
        setChanged()
        notifyObservers(event)
        clearChanged()
    }

    fun setPort(port: Int) {
        if (status === TcpClientState.CONNECTED) {
            throw RuntimeException("Cannot change port while connected")
        }
        this.port = port
    }

    fun connect() {
        if (status === TcpClientState.DISCONNECTED || status === TcpClientState.FAILED) {
            if (address == null || port == null) {
                throw java.lang.RuntimeException("Address or port missing")
            }
            ConnectThread().start()
        } else {
            throw java.lang.RuntimeException("This client is already connected or connecting")
        }
    }

    fun sendMessage(message: String?) {
        if (status === TcpClientState.CONNECTED) {
            if (message != null) {
                SendMessageThread(message).start()
            }
        } else {
            throw java.lang.RuntimeException("This client is not connected, and cannot send any message")
        }
    }

    fun disconnect() {
        DisconnectThread().run()
    }


    inner class ConnectThread : Thread() {
        override fun run() {
            try {
                status = TcpClientState.CONNECTING
                fireEvent(
                    TcpEvent(
                        TcpEventType.CONNECTION_STARTED,
                        null
                    )
                )
                socket = Socket()
                socket?.connect(
                    InetSocketAddress(InetAddress.getByName(address), port ?: 0),
                    timeout
                )
                bufferOut = PrintWriter(
                    BufferedWriter(OutputStreamWriter(socket?.getOutputStream())),
                    true
                )
                bufferIn = BufferedReader(InputStreamReader(socket?.getInputStream()))
                status = TcpClientState.CONNECTED
                fireEvent(
                    TcpEvent(
                        TcpEventType.CONNECTION_ESTABLISHED,
                        null
                    )
                )
                ReceiveMessagesThread().start()
            } catch (e: SocketTimeoutException) {
                fireEvent(
                    TcpEvent(
                        TcpEventType.CONNECTION_FAILED,
                        e
                    )
                )
                Log.e(TAG, "Socket timed out: $e")
                status = TcpClientState.FAILED
            } catch (e: IOException) {
                fireEvent(
                    TcpEvent(
                        TcpEventType.CONNECTION_FAILED,
                        e
                    )
                )
                Log.e(TAG, "Could not connect to host: $e")
                status = TcpClientState.FAILED
            }
        }
    }

    inner class ReceiveMessagesThread : Thread() {
        override fun run() {
            while (status === TcpClientState.CONNECTED) {
                try {
                    val message: String = bufferIn?.readLine() ?: ""
                    if (message != null) {
                        fireEvent(
                            TcpEvent(
                                TcpEventType.MESSAGE_RECEIVED,
                                message
                            )
                        )
                    }
                } catch (e: IOException) {
                    fireEvent(
                        TcpEvent(
                            TcpEventType.CONNECTION_LOST,
                            null
                        )
                    )
                    try {
                        bufferOut?.flush()
                        bufferOut?.close()
                        bufferIn?.close()
                        socket?.close()
                    } catch (er: IOException) {
                        Log.e(TAG, "Error clearing connection: $er")
                    }
                    status = TcpClientState.DISCONNECTED
                }
            }
        }
    }

    inner class SendMessageThread(message: String) : Thread() {
        private var messageLine: String = """
            $message
            
            """.trimIndent()

        override fun run() {
            if (bufferOut?.checkError()!!) {
                try {
                    bufferOut?.flush()
                    bufferOut?.close()
                    bufferIn?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending this message: $e")
                }
            } else {
                bufferOut?.print(messageLine)
                bufferOut?.flush()
                fireEvent(
                    TcpEvent(
                        TcpEventType.MESSAGE_SENT,
                        messageLine
                    )
                )
            }
        }

    }
    inner class DisconnectThread : Thread() {
        override fun run() {
            try {
                bufferOut?.flush()
                bufferOut?.close()
                bufferIn?.close()
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error disconnecting this client: $e")
            }
            fireEvent(
                TcpEvent(
                    TcpEventType.DISCONNECTED,
                    null
                )
            )
        }
    }

    companion object {
        val TAG = TcpClient::class.java.simpleName
    }
}

class TcpEvent(val tcpEventType: TcpEventType, val payload: Any?):Parcelable{
    constructor(parcel: Parcel) : this(
        TODO("tcpEventType"),
        TODO("payload")
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TcpEvent> {
        override fun createFromParcel(parcel: Parcel): TcpEvent {
            return TcpEvent(parcel)
        }

        override fun newArray(size: Int): Array<TcpEvent?> {
            return arrayOfNulls(size)
        }
    }

}

enum class TcpClientState {
    DISCONNECTED, CONNECTING, CONNECTED, CONNECTION_STARTED, FAILED
}

enum class TcpEventType {
    CONNECTION_STARTED, CONNECTION_ESTABLISHED, CONNECTION_FAILED, CONNECTION_LOST, MESSAGE_RECEIVED, MESSAGE_SENT, DISCONNECTED
}