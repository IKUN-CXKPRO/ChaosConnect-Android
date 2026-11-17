package com.chaosconnect.app.protocol

import java.io.*
import com.chaosconnect.app.ui.pairingStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*

/** Runs Android's production Kotlin client against the module's production C code. */
class ProductionInteropTest {
    private class Peer(root:String):ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        private val script=File("../scripts/ccn_peer.py").let {if(it.exists())it else File("scripts/ccn_peer.py")}
        private val process=ProcessBuilder("python3",script.absolutePath,root).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        private val writer=process.outputStream.bufferedWriter()
        private val reader=process.inputStream.bufferedReader()
        private val lock=Mutex()
        var writes=0;var commits=0;var aborts=0
        suspend fun control(line:String)=lock.withLock {withContext(Dispatchers.IO) {writer.write(line+"\n");writer.flush();check(reader.readLine()=="OK")}}
        override suspend fun send(packet:ByteArray)=lock.withLock {
            val op=WearEnvelope.unwrap(packet)!!.frame.opcode
            if(op==Op.WRITE)writes++;if(op==Op.COMMIT)commits++;if(op==Op.ABORT)aborts++
            val response=withContext(Dispatchers.IO) {writer.write(packet.hex()+"\n");writer.flush();reader.readLine() ?: error("C peer stopped")}
            check(!response.startsWith("ERROR")){response}
            response.split(' ').filter {it.isNotEmpty()}.forEach {hex -> incoming.send(hex.chunked(2).map {it.toInt(16).toByte()}.toByteArray())}
        }
        override fun close() {
            incoming.close()
            runCatching {writer.close()}
            if(!process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))process.destroy()
        }
    }
    private suspend fun withPeer(connectionPolicy:Int=0,mask:Int=31,block:suspend (ChaosSession,ChaosFiles,Peer)->Unit)=coroutineScope {
        val root=System.getenv("CHAOS_MODULE_ROOT")
        Assume.assumeTrue("Set CHAOS_MODULE_ROOT to enable production C interop",root!=null && File(root,"supervisor/bridge/chaos_connect.c").exists())
        val peer=Peer(root!!);val session=ChaosSession(peer,this,prefix=0x1234)
        try {peer.control("ANDROID $connectionPolicy $mask");session.connect();block(session,ChaosFiles(session),peer)}finally {session.close()}
    }
    @Test fun nativeCInstallsBothWebDeliveryPackagesWithReadOnlyFilePermission()=runBlocking {
        withPeer(mask=1){session,files,peer->
            peer.control("INSTALL 0")
            for(kind in listOf("font","icons")){
                val local=File.createTempFile("web-package-",".bin")
                try{
                    local.writeBytes(javaClass.getResourceAsStream("/delivery/web-$kind.bin")!!.readBytes())
                    val pack=DeliveryPackageReader.parse(local);val progress=mutableListOf<InstallProgress>()
                    ChaosPackageInstaller(session).install(pack){progress.add(it)}
                    assertEquals(6,progress.last().state);assertEquals(pack.total,progress.last().done)
                    val directory=if(kind=="font")"/data/chaos/font" else "/data/chaos/icons"
                    val index=ByteArrayOutputStream();files.download("$directory/index.txt",index)
                    assertTrue(index.toString("UTF-8").contains(pack.name))
                    val installed=ByteArrayOutputStream();files.download(if(kind=="font")"$directory/st1.ttf" else "$directory/01/app.bin",installed)
                    val e=pack.entries.single();val expected=local.readBytes().copyOfRange(e.offset.toInt(),e.offset.toInt()+e.size)
                    assertArrayEquals(expected,installed.toByteArray())
                }finally{local.delete()}
            }
            assertEquals(0,peer.writes);assertEquals(0,peer.commits)
        }
    }
    @Test fun nativeCNamedApprovalAndCancelLeavePreviousResourcesIntact()=runBlocking {
        withPeer(mask=1){session,files,peer->
            val local=File.createTempFile("web-approved-",".bin")
            try{
                local.writeBytes(javaClass.getResourceAsStream("/delivery/web-font.bin")!!.readBytes());val pack=DeliveryPackageReader.parse(local)
                peer.control("INSTALL 2");val waiting=CompletableDeferred<Unit>()
                val install=async{ChaosPackageInstaller(session).install(pack){if(it.state==1)waiting.complete(Unit)}}
                withTimeout(5_000){waiting.await()};peer.control("DECIDE 1 1");withTimeout(5_000){install.await()}
                val index=ByteArrayOutputStream();files.download("/data/chaos/font/index.txt",index);assertTrue(index.toString("UTF-8").contains("文楷"))
                val nextWaiting=CompletableDeferred<Unit>()
                val cancel=launch{ChaosPackageInstaller(session).install(pack){if(it.state==1)nextWaiting.complete(Unit)}}
                withTimeout(5_000){nextWaiting.await()};cancel.cancelAndJoin()
                val after=ByteArrayOutputStream();files.download("/data/chaos/font/index.txt",after);assertArrayEquals(index.toByteArray(),after.toByteArray())
                session.ping();assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            }finally{local.delete()}
        }
    }
    @Test fun nativeCUserMustApproveNamedInstallAndCanRejectIt()=runBlocking {
        withPeer(mask=1){session,_,peer->
            val local=File.createTempFile("web-consent-",".bin")
            try{
                local.writeBytes(javaClass.getResourceAsStream("/delivery/web-font.bin")!!.readBytes());val pack=DeliveryPackageReader.parse(local)
                peer.control("INSTALL 2")
                val pending=CompletableDeferred<Unit>()
                val job=async{runCatching{ChaosPackageInstaller(session).install(pack){if(it.state==1)pending.complete(Unit)}}}
                withTimeout(5_000){pending.await()}
                // The first install consumes token 1 in this fresh C peer.
                peer.control("DECIDE 1 2")
                assertTrue(job.await().isFailure);session.ping();assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            }finally{local.delete()}
        }
    }
    @Test fun nativeCLinkSpeedChecksEchoAndDoesNotWriteFiles()=runBlocking {
        withPeer(mask=1) {session,files,_ ->
            coroutineScope {
                val progress=CompletableDeferred<SpeedProgress>()
                val job=launch {ConnectionSpeedTest(session).run {progress.complete(it)}}
                try {
                    val measured=withTimeout(10_000){progress.await()}
                    assertTrue(measured.received>0);assertEquals(measured.sent,measured.received)
                    assertTrue(measured.elapsedMillis>=200);assertTrue(measured.bytesPerSecond>0)
                }finally{job.cancelAndJoin()}
            }
            assertTrue(files.list("/data").entries.isEmpty())
        }
    }
    @Test fun nativeCTerminalFramingAndPermissionRevocation()=runBlocking {
        // C file engine and Kotlin terminal protocol are production code;
        // PTY is a deterministic echo seam, not proof of hardware command execution.
        withPeer(mask=33) {session,_,peer ->
            val terminal=ChaosTerminal(session);assertTrue(terminal.open().lease>0)
            terminal.send("ls /data\n".toByteArray());assertEquals("ls /data\n",terminal.read().bytes.toString(Charsets.UTF_8))
            peer.control("ANDROID 0 1");session.ping()
            try{terminal.send("reboot\n".toByteArray());fail()}catch(_:ProtocolException){}
        }
    }
    @Test fun nativeCHandshakeFilesWindowTransferAndUtf8()=runBlocking {
        withPeer {session,files,peer ->
            assertEquals(8,session.state.value.negotiation!!.window)
            assertEquals(2048,session.state.value.negotiation!!.chunk)
            files.mkdir("/data/目录")
            val bytes=ByteArray(256*1024+19){(it*17).toByte()}
            peer.control("FAULT 31 0") // Production VFS has short writes.
            files.upload("/data/目录/测试.bin",ByteArrayInputStream(bytes),bytes.size.toLong())
            assertEquals(1,peer.commits);assertEquals(129,peer.writes)
            val output=ByteArrayOutputStream();assertEquals(bytes.size.toLong(),files.download("/data/目录/测试.bin",output));assertArrayEquals(bytes,output.toByteArray())
            assertEquals("测试.bin",files.list("/data/目录").entries.single().name)
            assertEquals(bytes.size.toLong(),files.stat("/data/目录/测试.bin").size)
            files.copy("/data/目录/测试.bin","/data/副本")
            files.rename("/data/副本","/data/移动")
            files.remove("/data/移动");files.remove("/data/目录/测试.bin");files.remove("/data/目录",true)
            assertArrayEquals(bytes.copyOf(2048),files.echo(bytes.copyOf(2048)))
        }
    }
    @Test fun nativeCPermissionTokenWaitDenyAndRevoke()=runBlocking {
        withPeer {session,files,peer ->
            peer.control("POLICY 1")
            try {session.setDeveloper(true);fail()}catch(e:DeviceStatusException){assertEquals(3,e.code)}
            peer.control("POLICY 2")
            val token=session.setDeveloper(true)!!;assertTrue(token>0)
            assertTrue(session.state.value.permission!!.waiting)
            try {files.list("/data");fail()}catch(_:ProtocolException){}
            peer.control("DECIDE $token 1");assertTrue(session.ping().developer);assertTrue(session.getDeveloper())
            session.setDeveloper(false);assertFalse(session.state.value.permission!!.developer)
        }
    }
    @Test fun nativeCAndroidConnectionConsentAndRevocation()=runBlocking {
        withPeer(connectionPolicy=2,mask=1) {session,files,peer ->
            assertEquals(SessionPhase.AWAITING_APPROVAL,session.state.value.phase)
            try{files.list("/data");fail()}catch(_:ProtocolException){}
            val token=session.state.value.access!!.token;assertTrue(token>0)
            peer.control("DECIDE $token 1");session.ping()
            assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            val stale=BluetoothState(message="蓝牙链路已建立，请在手环确认ChaosConnect连接")
            assertEquals("已连接 · 权限由手环控制",pairingStatus(stale,session.state.value).message)
            files.list("/data")
            try{files.mkdir("/data/not-allowed");fail()}catch(_:ProtocolException){}
            peer.control("ANDROID 0 3");session.ping();files.mkdir("/data/allowed")
            peer.control("ANDROID 1 31")
            try{session.ping();fail()}catch(_:ProtocolException){}
            assertEquals(SessionPhase.DISCONNECTED,session.state.value.phase)
        }
    }
    @Test fun earlyInputAndCommitFailureNeverOverwriteOriginal()=runBlocking {
        withPeer {_,files,peer ->
            val old="original".toByteArray();files.upload("/data/file",old.inputStream(),old.size.toLong())
            try {files.upload("/data/file",byteArrayOf(1,2).inputStream(),30,true);fail()}catch(_:ProtocolException){}
            assertEquals(1,peer.commits);assertTrue(peer.aborts>0)
            val result=ByteArrayOutputStream();files.download("/data/file",result);assertArrayEquals(old,result.toByteArray())
            peer.control("FAULT 0 17")
            try {files.upload("/data/file",byteArrayOf(3,4).inputStream(),2,true);fail()}catch(e:DeviceStatusException){assertEquals(4,e.code)}
            peer.control("FAULT 0 0")
            val preserved=ByteArrayOutputStream();files.download("/data/file",preserved);assertArrayEquals(old,preserved.toByteArray())
        }
    }
}
