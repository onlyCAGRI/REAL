package com.cagri.real.boards

/** Inspect bytes, not keyboard/provider file names. Never decode/re-encode GIF frames. */
fun boardImageMime(header: ByteArray): String? {
    fun at(i:Int)=header.getOrNull(i)?.toInt()?.and(255)
    if(header.size>=6 && String(header,0,6,Charsets.US_ASCII) in listOf("GIF87a","GIF89a"))return "image/gif"
    if(at(0)==255 && at(1)==216 && at(2)==255)return "image/jpeg"
    if(header.size>=8 && header.take(8)==listOf(137,80,78,71,13,10,26,10).map{it.toByte()})return "image/png"
    if(header.size>=12 && String(header,0,4,Charsets.US_ASCII)=="RIFF" && String(header,8,4,Charsets.US_ASCII)=="WEBP")return "image/webp"
    return null
}
