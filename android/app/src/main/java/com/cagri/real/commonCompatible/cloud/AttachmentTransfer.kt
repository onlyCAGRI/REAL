package com.cagri.real.cloud

/** Small bounded files in v1. Platform code owns files/URIs and network I/O. */
interface AttachmentTransfer {
    suspend fun upload(id: String, mime: String, bytes: ByteArray)
    suspend fun download(id: String): ByteArray
}
