package com.chaosconnect.app.ui

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class TerminalState(val active:Boolean=false,val busy:Boolean=false,val message:String="连接手环并允许NSH终端权限后启动",val output:String="")
/** Decode split UTF-8 records without corrupting CJK; strip bounded ANSI CSI/OSC.
 * Transcript is line-oriented, with CR/backspace handling and a 32K character cap. */
class TerminalTranscript {
    private val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending=byteArrayOf()
    private val text=StringBuilder()
    private var escape=0
    private var cr=false
    fun clear(){pending=byteArrayOf();decoder.reset();text.clear();escape=0;cr=false}
    fun append(bytes:ByteArray):String {
        val input=ByteBuffer.wrap(pending+bytes);val out=CharBuffer.allocate(input.remaining()+2)
        decoder.decode(input,out,false);pending=ByteArray(input.remaining()).also{input.get(it)};out.flip()
        while(out.hasRemaining()) {
            val c=out.get()
            when(escape) {
                1->{escape=when(c){'['->2;']'->3;else->0};continue}
                2->{if(c in '@'..'~')escape=0;continue}
                3->{if(c=='\u0007')escape=0 else if(c=='\u001b')escape=4;continue}
                4->{escape=if(c=='\\')0 else 3;continue}
            }
            if(c=='\u001b'){escape=1;continue}
            if(c=='\r'){cr=true;continue}
            if(c=='\n'){text.append('\n');cr=false;continue}
            if(cr){val start=text.lastIndexOf("\n")+1;text.delete(start,text.length);cr=false}
            if(c=='\b' || c=='\u007f'){if(text.isNotEmpty() && text.last()!='\n')text.deleteCharAt(text.length-1)}
            else if(c=='\t' || c>=' ')text.append(c)
        }
        if(text.length>32768){var cut=text.length-32768;if(cut<text.length && text[cut].isLowSurrogate())cut++;text.delete(0,cut)}
        return text.toString()
    }
}
