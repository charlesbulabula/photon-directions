package com.neo.maps.core.memory

import java.nio.ByteBuffer

/**
 * Very simple secure-ish wrapper that:
 *  - stores JPEG bytes in a small buffer
 *  - can be zeroed when we're done
 */
class SecureByteBuffer(private var buffer: ByteArray?) {

    fun get(): ByteArray {
        return buffer ?: ByteArray(0)
    }

    fun zero() {
        buffer?.fill(0)
        buffer = null
    }

    companion object {
        fun from(bytes: ByteArray): SecureByteBuffer {
            val copy = ByteArray(bytes.size)
            System.arraycopy(bytes, 0, copy, 0, bytes.size)
            return SecureByteBuffer(copy)
        }
    }
}
