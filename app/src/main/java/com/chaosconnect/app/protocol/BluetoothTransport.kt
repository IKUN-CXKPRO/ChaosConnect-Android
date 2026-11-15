package com.chaosconnect.app.protocol

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import java.io.EOFException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Secure public RFCOMM API; authentication/encryption belongs to Android's BT stack.
 * Stream records are u32LE length + the existing complete WearPacket envelope. */
internal suspend fun <T> socketIo(scope:CoroutineScope,socket:BluetoothSocket,block:()->T):T = suspendCancellableCoroutine { continuation ->
    val job=scope.launch(Dispatchers.IO) {
        try {val value=block();if(continuation.isActive)continuation.resume(value)}
        catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
    }
    continuation.invokeOnCancellation {runCatching{socket.close()};job.cancel()}
}
class BluetoothTransport(private val socket:BluetoothSocket,parent:CoroutineScope):ChaosTransport {
    companion object {val SERVICE_UUID:UUID=UUID.fromString("43434e31-0001-4a10-8000-4348414f5301")}
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    private val closed=AtomicBoolean(false)
    private val queue=Channel<ByteArray>(32)
    private val sendLock=Mutex()
    override val maximumPacketBytes=CcnCodec.MAX_WIRE
    override val incoming get()=queue
    init {
        scope.launch(Dispatchers.IO) {
            try {
                val input=socket.inputStream
                fun readFully(bytes:ByteArray) {var at=0;while(at<bytes.size){val n=input.read(bytes,at,bytes.size-at);if(n<0)throw EOFException("蓝牙链路已关闭");if(n>0)at+=n}}
                while(isActive && !closed.get()) {
                    val header=ByteArray(4);readFully(header)
                    val n=WireReader(header).u32()
                    checkWire(n in 16..maximumPacketBytes.toLong(),"蓝牙记录长度无效")
                    val packet=ByteArray(n.toInt());readFully(packet);queue.send(packet)
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){queue.close(e)}
            finally {queue.close();runCatching{socket.close()}}
        }
    }
    override suspend fun send(packet:ByteArray)=sendLock.withLock {
        checkWire(!closed.get() && packet.size<=maximumPacketBytes,"蓝牙连接已关闭或消息过长")
        socketIo(scope,socket) {
            val output=socket.outputStream
            output.write(WireWriter().u32(packet.size.toLong()).build());output.write(packet);output.flush()
        }
    }
    override fun close(){if(closed.compareAndSet(false,true)){runCatching{socket.close()};queue.close();scope.cancel()}}
}
data class BluetoothPeer(val address:String,val name:String,val bonded:Boolean)
data class BluetoothState(val peers:List<BluetoothPeer> = emptyList(),val scanning:Boolean=false,val connecting:Boolean=false,val message:String="请先在手环开启“发起蓝牙配对”")
@SuppressLint("MissingPermission")
class ChaosBluetooth(private val context:Context,private val scope:CoroutineScope,private val connected:(ChaosTransport,BluetoothPeer)->Unit) {
    private val adapter get()=context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableState=MutableStateFlow(BluetoothState())
    val state:StateFlow<BluetoothState> = mutableState.asStateFlow()
    private var registered=false
    private var connecting:Job?=null
    private var pendingSocket:BluetoothSocket?=null
    private fun permitted()=listOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN).all{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    private fun peer(device:BluetoothDevice,advertisedName:String?=null):BluetoothPeer? {
        val name=BandDiscovery.name(device.name) ?: BandDiscovery.name(advertisedName) ?: return null
        return BluetoothPeer(device.address,name,device.bondState==BluetoothDevice.BOND_BONDED)
    }
    private fun add(device:BluetoothDevice,advertisedName:String?=null){
        if(device.type==BluetoothDevice.DEVICE_TYPE_LE)return
        val p=peer(device,advertisedName) ?: return
        mutableState.update{it.copy(peers=BandDiscovery.visible(it.peers.filterNot{old->old.address==p.address}+p))}
    }
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
        if(!permitted())return
        when(intent.action){
            BluetoothDevice.ACTION_FOUND,BluetoothDevice.ACTION_BOND_STATE_CHANGED,BluetoothDevice.ACTION_NAME_CHANGED->intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice::class.java)?.let{add(it,intent.getStringExtra(BluetoothDevice.EXTRA_NAME))}
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED->mutableState.update{if(it.scanning)it.copy(scanning=false,message=if(it.peers.isEmpty())"未找到手环（Xiaomi Smart Band / Chaos-Module），请确认手环已开启配对" else "搜索完成，选择手环连接")else it}
        }
    }}
    fun permissionDenied(){mutableState.update{it.copy(message="请允许附近设备权限后再搜索")}}
    fun scan(){
        if(!permitted()){permissionDenied();return}
        if(mutableState.value.connecting)return
        try {
            val bt=adapter ?: error("手机不支持蓝牙")
            check(bt.isEnabled){"请先在系统设置开启蓝牙"}
            if(!registered){context.registerReceiver(receiver,IntentFilter().apply{addAction(BluetoothDevice.ACTION_FOUND);addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);addAction(BluetoothDevice.ACTION_NAME_CHANGED);addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)},Context.RECEIVER_EXPORTED);registered=true}
            if(bt.isDiscovering)bt.cancelDiscovery()
            mutableState.update{it.copy(peers=emptyList())}
            bt.bondedDevices.forEach{add(it)}
            check(bt.startDiscovery()){ "无法开始搜索，请稍后重试" }
            mutableState.update{it.copy(scanning=true,message="正在搜索手环（Xiaomi Smart Band / Chaos-Module），请保持手环配对窗口开启")}
        }catch(e:Exception){mutableState.update{it.copy(scanning=false,message=e.message ?: "蓝牙搜索失败")}}
    }
    fun connect(address:String){
        if(!permitted()){permissionDenied();return}
        if(connecting?.isActive==true)return
        connecting=scope.launch {
            var socket:BluetoothSocket?=null
            try {
                check(BluetoothAdapter.checkBluetoothAddress(address)){"蓝牙地址无效"}
                val bt=adapter ?: error("手机不支持蓝牙");bt.cancelDiscovery()
                val device=bt.getRemoteDevice(address)
                val p=peer(device,mutableState.value.peers.find{it.address==address}?.name)
                    ?: error("仅支持 Xiaomi Smart Band 系列或 Chaos-Module 设备")
                mutableState.update{it.copy(connecting=true,scanning=false,message="正在连接 ${p.name}，请完成系统蓝牙配对")}
                socket=device.createRfcommSocketToServiceRecord(BluetoothTransport.SERVICE_UUID);pendingSocket=socket
                withTimeout(60_000){socketIo(scope,socket){socket.connect()}}
                val transport=BluetoothTransport(socket,scope);pendingSocket=null
                mutableState.update{it.copy(connecting=false,message="蓝牙链路已建立，请在手环确认ChaosConnect连接")}
                connected(transport,p)
            }catch(e:CancellationException){runCatching{socket?.close()};throw e}
            catch(e:Exception){runCatching{socket?.close()};mutableState.update{it.copy(connecting=false,message=e.message ?: "连接失败")}}
            finally {pendingSocket=null;mutableState.update{it.copy(connecting=false)}}
        }
    }
    fun cancelConnection(){connecting?.cancel();runCatching{pendingSocket?.close()};pendingSocket=null
        mutableState.update{it.copy(connecting=false,message="已断开，可选择手环重新连接")}
    }
    fun close(){connecting?.cancel();runCatching{pendingSocket?.close()};pendingSocket=null
        if(permitted())runCatching{adapter?.cancelDiscovery()}
        if(registered){context.unregisterReceiver(receiver);registered=false}
    }
}
