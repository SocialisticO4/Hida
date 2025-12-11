package com.example.hida.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Parser for detecting and extracting motion photos.
 * Motion photos are images (JPEG/HEIC) with an embedded MP4 video at the end.
 */
class MotionPhotoParser {
    
    companion object {
        // MP4 "ftyp" signature
        private val FTYP = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        private const val MIN_VIDEO_SIZE = 10 * 1024 // 10KB minimum
    }
    
    data class MotionPhotoInfo(
        val videoOffset: Long,
        val videoLength: Long,
        val imageLength: Long
    )
    
    /**
     * Get motion photo info from raw bytes.
     */
    fun getMotionPhotoInfo(bytes: ByteArray): MotionPhotoInfo? {
        if (bytes.size < MIN_VIDEO_SIZE * 2) return null
        
        // Search for ftyp box (start after first 1KB to skip image header)
        val startOffset = minOf(1024, bytes.size / 4)
        
        for (i in startOffset until bytes.size - 8) {
            if (matchesFtyp(bytes, i)) {
                val boxStart = i - 4
                if (boxStart >= startOffset && isValidFtypBox(bytes, boxStart)) {
                    val videoLength = bytes.size - boxStart
                    if (videoLength >= MIN_VIDEO_SIZE) {
                        return MotionPhotoInfo(
                            videoOffset = boxStart.toLong(),
                            videoLength = videoLength.toLong(),
                            imageLength = boxStart.toLong()
                        )
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Get motion photo info from file.
     */
    fun getMotionPhotoInfo(file: File): MotionPhotoInfo? {
        if (!file.exists() || file.length() < MIN_VIDEO_SIZE * 2) return null
        
        return try {
            val bytes = file.readBytes()
            getMotionPhotoInfo(bytes)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun matchesFtyp(bytes: ByteArray, offset: Int): Boolean {
        return bytes[offset] == FTYP[0] &&
               bytes[offset + 1] == FTYP[1] &&
               bytes[offset + 2] == FTYP[2] &&
               bytes[offset + 3] == FTYP[3]
    }
    
    private fun isValidFtypBox(bytes: ByteArray, boxStart: Int): Boolean {
        if (boxStart < 0) return false
        val boxSize = ((bytes[boxStart].toInt() and 0xFF) shl 24) or
                      ((bytes[boxStart + 1].toInt() and 0xFF) shl 16) or
                      ((bytes[boxStart + 2].toInt() and 0xFF) shl 8) or
                      (bytes[boxStart + 3].toInt() and 0xFF)
        return boxSize in 8..64
    }
    
    /**
     * Extract video bytes from a decrypted motion photo.
     */
    fun extractVideo(bytes: ByteArray, info: MotionPhotoInfo): ByteArray {
        return bytes.copyOfRange(info.videoOffset.toInt(), bytes.size)
    }
    
    /**
     * Extract image bytes from a decrypted motion photo.
     */
    fun extractImage(bytes: ByteArray, info: MotionPhotoInfo): ByteArray {
        return bytes.copyOfRange(0, info.imageLength.toInt())
    }
}
