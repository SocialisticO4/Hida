package app.hida.vault.nativemodule

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * MediaRepository - Secure Media Storage with Chunked Encryption
 *
 * Uses Chunked ChaCha20-Poly1305 for all files.
 * File format: [12-byte nonce][256KB chunk + 16B tag][256KB chunk + 16B tag]...
 */
class MediaRepository(private val context: Context) {

    companion object {
        /** Recognized vault video extensions (streaming encrypt + playback / thumbs). */
        val VIDEO_EXTENSIONS: Set<String> = setOf(
            "mp4", "m4v", "3gp", "webm", "mkv", "avi", "mov",
            "mpeg", "mpg", "wmv", "ogv", "mts", "m2ts", "ts"
        )

        /**
         * First-pass preview size for thumbnails (plaintext bytes). Larger helps when moov sits
         * after mdat but increases IO for each grid cell moov-first files still succeed early.
         */
        private const val VIDEO_THUMB_PREVIEW_BYTES_PT = 12L * 1024 * 1024

        private const val VIDEO_THUMB_EXPANDED_PREVIEW_BYTES_PT = 40L * 1024 * 1024

        /** Fallback full decrypt thumbnail only when the encrypted vault file is bounded. */
        private const val VIDEO_THUMB_FULL_MAX_ENCRYPTED_BYTES = 52L * 1024 * 1024

        /** JPEG thumbnail quality for grid thumbnails. */
        private const val VIDEO_THUMB_JPEG_QUALITY = 82

        /**
         * Hard ceiling on [getDecryptedBytes]. Sidecars (`.meta`, `.name`) are tiny
         * (a few hundred bytes encrypted); 1 MiB is generous and stops a tampered or
         * corrupted sidecar from OOMing the process via the in-memory buffer.
         */
        private const val MAX_BYTES_FOR_GET_DECRYPTED_BYTES = 1L * 1024 * 1024
    }

    private val directory = File(context.filesDir, "secure_media")
    private val prefs = PreferencesManager(context)
    val cryptoManager: CryptoManager = CryptoManager.getInstance(context)
    private val motionPhotoParser = MotionPhotoParser()

    init {
        // One-time wipe of legacy v1 crypto state (plaintext PIN + Keystore-only wrap).
        // Any existing encrypted files are also dropped because they were encrypted with
        // the v1 key, which we no longer carry forward.
        if (prefs.hasLegacyV1State() && !prefs.hasPin()) {
            prefs.clearLegacyV1State()
            if (directory.exists()) clearAllMedia()
        }
        if (!directory.exists()) {
            directory.mkdirs()
        } else if (!prefs.hasPin() && (directory.list()?.isNotEmpty() == true)) {
            // Defensive: no PIN set up but stale ciphertext on disk. Wipe.
            clearAllMedia()
        }
        // Prevent Samsung media scanner from indexing encrypted files and temp decryptions
        ensureNoMedia(directory)
        ensureNoMedia(context.cacheDir)
        // Remove any plaintext temp files left behind by a prior force-kill / crash / reboot
        cleanupTempFiles()
    }

    private fun ensureNoMedia(dir: File) {
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            noMedia.createNewFile()
        }
    }

    fun clearAllMedia() {
        // Preserve the .nomedia sentinel so the system media scanner doesn't
        // briefly index the directory between this wipe and the next init.
        directory.listFiles()?.forEach {
            if (it.exists() && it.name != ".nomedia") it.delete()
        }
        ensureNoMedia(directory)
    }

    suspend fun getMediaFiles(): List<File> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    !it.name.endsWith(".meta") &&
                    !it.name.endsWith(".name") &&
                    it.name != ".nomedia"
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun saveMediaFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        // Only accept content:// (Storage Access Framework) and file:// (DocumentPicker
        // copy-to-cache) sources. Reject http(s)://, content scheme tampering, or
        // other URIs the JS layer might forward — even though the JS surface uses
        // Expo DocumentPicker today, defence-in-depth at this boundary keeps a future
        // careless caller from streaming attacker-controlled bytes through the vault.
        when (uri.scheme?.lowercase()) {
            "content", "file" -> {}
            else -> return@withContext Result.failure(
                IllegalArgumentException("Unsupported URI scheme")
            )
        }
        try {
            val contentResolver = context.contentResolver

            var ext = "jpg"
            var originalName: String? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val display = cursor.getString(nameIndex)?.trim()
                        if (!display.isNullOrEmpty()) {
                            originalName = display
                            val lastDot = display.lastIndexOf(".")
                            if (lastDot != -1) {
                                ext = display.substring(lastDot + 1).lowercase()
                            }
                        }
                    }
                }
            }

            if (originalName.isNullOrBlank()) {
                val seg = uri.lastPathSegment?.let { raw ->
                    runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
                }
                if (!seg.isNullOrBlank()) {
                    val nameFromSeg = seg.substringAfterLast("/")
                    originalName = nameFromSeg
                    val lastDot = nameFromSeg.lastIndexOf(".")
                    if (lastDot != -1) {
                        val segExt = nameFromSeg.substring(lastDot + 1).lowercase()
                        if (segExt.isNotEmpty()) ext = segExt
                    }
                }
            }

            // Extension fallback chain: if DISPLAY_NAME didn't provide an
            // extension (ext is still default "jpg"), try two more sources:
            // 1) Extract extension from the URI path (works for file:// URIs
            //    from DocumentPicker with copyToCacheDirectory: true)
            // 2) Use MIME type from ContentResolver (works for content:// URIs)
            if (ext == "jpg") {
                val uriPath = uri.lastPathSegment
                if (uriPath != null) {
                    val uriLastDot = uriPath.lastIndexOf(".")
                    if (uriLastDot != -1) {
                        val uriExt = uriPath.substring(uriLastDot + 1).lowercase()
                        if (uriExt.isNotEmpty() && uriExt != "jpg") {
                            ext = uriExt
                        }
                    }
                }
            }
            if (ext == "jpg") {
                val mimeType = contentResolver.getType(uri)
                if (mimeType != null) {
                    val mimeExt = android.webkit.MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(mimeType)
                    if (mimeExt != null) ext = mimeExt
                }
            }

            val encryptedFile = File(directory, "${UUID.randomUUID()}.$ext")
            val lowExt = ext.lowercase()
            val isVideoFile = lowExt in VIDEO_EXTENSIONS
            val isAudioFile = ext in listOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "wma")
            val useStreaming = isVideoFile || isAudioFile

            if (useStreaming) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    encryptFromStream(inputStream, encryptedFile)
                } ?: throw Exception("Failed to open media stream")
            } else {
                val originalBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Failed to read file")

                encryptToFile(originalBytes, encryptedFile)

                val motionInfo = motionPhotoParser.getMotionPhotoInfo(originalBytes)
                if (motionInfo != null) {
                    saveMetadata(encryptedFile, motionInfo)
                }
            }

            originalName?.let { saveOriginalName(encryptedFile, it) }

            Result.success(encryptedFile)
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
            Result.failure(e)
        }
    }

    private fun encryptFromStream(inputStream: InputStream, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            val nonce = cryptoManager.generateNonce()
            fos.write(nonce)

            val buffer = ByteArray(CryptoManager.CHUNK_SIZE)
            var chunkIndex = 0L

            while (true) {
                var filled = 0
                while (filled < buffer.size) {
                    val n = inputStream.read(buffer, filled, buffer.size - filled)
                    if (n == -1) break
                    filled += n
                }
                if (filled == 0) break

                val chunkData = if (filled == buffer.size) buffer else buffer.copyOf(filled)
                val encryptedChunk = cryptoManager.encryptChunk(chunkData, nonce, chunkIndex)
                fos.write(encryptedChunk)

                chunkIndex++
            }
        }
    }

    private fun encryptToFile(plaintext: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            val nonce = cryptoManager.generateNonce()
            fos.write(nonce)

            var offset = 0
            var chunkIndex = 0L
            while (offset < plaintext.size) {
                val chunkEnd = minOf(offset + CryptoManager.CHUNK_SIZE, plaintext.size)
                val chunkData = plaintext.copyOfRange(offset, chunkEnd)

                val encryptedChunk = cryptoManager.encryptChunk(chunkData, nonce, chunkIndex)
                fos.write(encryptedChunk)

                offset = chunkEnd
                chunkIndex++
            }
        }
    }

    /**
     * Decrypt a vault file fully into memory. Reserved for tiny metadata sidecars
     * (`.meta`, `.name`) — never call this for primary media. The size cap below
     * stops a corrupted or tampered sidecar from OOMing the process and leaving
     * plaintext fragments in heap dumps.
     */
    suspend fun getDecryptedBytes(file: File): ByteArray? = withContext(Dispatchers.IO) {
        if (file.length() > MAX_BYTES_FOR_GET_DECRYPTED_BYTES) return@withContext null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val nonce = ByteArray(CryptoManager.NONCE_SIZE)
                raf.read(nonce)

                val result = java.io.ByteArrayOutputStream()
                var chunkIndex = 0L

                while (raf.filePointer < raf.length()) {
                    val remaining = raf.length() - raf.filePointer
                    val encryptedChunkSize = minOf(CryptoManager.ENCRYPTED_CHUNK_SIZE.toLong(), remaining).toInt()

                    if (encryptedChunkSize <= CryptoManager.AUTH_TAG_SIZE) break

                    val encryptedChunk = ByteArray(encryptedChunkSize)
                    raf.read(encryptedChunk)

                    val decryptedChunk = cryptoManager.decryptChunk(encryptedChunk, nonce, chunkIndex)
                    result.write(decryptedChunk)

                    chunkIndex++
                }

                result.toByteArray()
            }
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
            null
        }
    }

    fun getDecryptedStream(file: File): InputStream {
        return ChunkedDecryptionInputStream(file, cryptoManager)
    }

    /**
     * Decrypt media to a temp file and return the path.
     * Used by JS layer for thumbnails and video playback.
     */
    suspend fun decryptToTempFile(file: File, suffix: String = ".tmp"): File? = withContext(Dispatchers.IO) {
        try {
            // FileProvider (file_paths.xml) exposes cacheDir/decrypted/ for ACTION_VIEW.
            // Putting the temp file anywhere else makes FileProvider.getUriForFile throw.
            val decryptedDir = File(context.cacheDir, "decrypted").apply { mkdirs() }
            val tempFile = File.createTempFile("hida_", suffix, decryptedDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { fos ->
                getDecryptedStream(file).use { input ->
                    input.copyTo(fos)
                }
            }

            tempFile
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
            null
        }
    }

    /**
     * Decrypt up to [maxPlaintextBytes] into a bounded temp video (truncated plaintext).
     */
    suspend fun decryptToTempBoundedPlaintext(
        file: File,
        maxPlaintextBytes: Long,
        suffix: String,
    ): File? = withContext(Dispatchers.IO) {
        if (maxPlaintextBytes <= 0) return@withContext null
        try {
            val decryptedDir = File(context.cacheDir, "decrypted").apply { mkdirs() }
            val tempFile = File.createTempFile("hida_vidprev_", suffix, decryptedDir)
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { fos ->
                getDecryptedStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (copied < maxPlaintextBytes) {
                        val cap = minOf(buffer.size.toLong(), maxPlaintextBytes - copied).toInt()
                        val n = input.read(buffer, 0, cap)
                        if (n == -1) break
                        fos.write(buffer, 0, n)
                        copied += n
                    }
                }
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                null
            } else {
                tempFile
            }
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
            null
        }
    }

    /**
     * Extract first video frame into a JPEG in [decrypted] cache; caller deletes [videoScratch].
     */
    private fun videoScratchToThumbnailJpeg(videoScratch: File): File? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoScratch.absolutePath)
            @Suppress("DEPRECATION")
            val bmp = retriever.getFrameAtTime(
                833_334L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
                ?: retriever.getFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            retriever.release()
            retriever = null
            if (bmp == null) return null
            val decryptedDir = File(context.cacheDir, "decrypted").apply { mkdirs() }
            val out = File.createTempFile("hida_vidthumb_", ".jpg", decryptedDir)
            out.deleteOnExit()
            FileOutputStream(out).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, VIDEO_THUMB_JPEG_QUALITY, fos)
            }
            bmp.recycle()
            out
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Lightweight grid thumbnail for encrypted video: bounded decrypt(s) + frame grab.
     * Large files or moov-at-end-heavy encodes may yield null → JS shows fallback icon.
     */
    private suspend fun tryVideoThumbnailFromPrefix(
        file: File,
        maxPt: Long,
        sfx: String,
    ): File? {
        val scratch = decryptToTempBoundedPlaintext(file, maxPt, sfx) ?: return null
        return try {
            videoScratchToThumbnailJpeg(scratch)
        } finally {
            scratch.delete()
        }
    }

    suspend fun generateEncryptedVideoThumbnailJpeg(file: File): File? =
        withContext(Dispatchers.IO) {
            val sfx = "." + file.extension.lowercase().ifBlank { "mp4" }

            tryVideoThumbnailFromPrefix(file, VIDEO_THUMB_PREVIEW_BYTES_PT, sfx)
                ?: tryVideoThumbnailFromPrefix(file, VIDEO_THUMB_EXPANDED_PREVIEW_BYTES_PT, sfx)
                ?: run {
                    if (file.length() > VIDEO_THUMB_FULL_MAX_ENCRYPTED_BYTES) {
                        null
                    } else {
                        val full = decryptToTempFile(file, sfx) ?: return@run null
                        try {
                            videoScratchToThumbnailJpeg(full)
                        } finally {
                            full.delete()
                        }
                    }
                }
        }

    suspend fun exportMedia(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val isVideoFile = isVideo(file)
            val isAudioFile = isAudio(file)
            val isDocFile = isDocument(file)
            val ext = file.extension.lowercase()

            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext)
                ?: if (isVideoFile) "video/mp4"
                else if (isAudioFile) "audio/mpeg"
                else if (isDocFile) "application/octet-stream"
                else "image/jpeg"

            val collection = if (isVideoFile) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if (isAudioFile) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else if (isDocFile) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val relativePath = if (isVideoFile) "Movies/Restored"
                else if (isAudioFile) "Music/Restored"
                else if (isDocFile) "Download/Restored"
                else "Pictures/Restored"

            // Prefer the user's original filename when we recorded it; fall back to a
            // timestamp-based name so the random vault UUID is never observable in
            // MediaStore (correlating an export back to a vault entry).
            val originalName = loadOriginalName(file)
            val displayName = originalName
                ?: "restored_${System.currentTimeMillis()}.$ext"
            val details = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, details) ?: return@withContext false

            // Track whether the bytes actually landed in MediaStore. If anything below
            // throws or returns a falsy state, we delete the orphaned MediaStore row
            // and KEEP the vault file — losing the encrypted source after a partial
            // export would be permanent data loss.
            var publishOk = false
            try {
                val out = resolver.openOutputStream(uri)
                    ?: throw java.io.IOException("openOutputStream returned null")
                out.use { output ->
                    getDecryptedStream(file).use { input ->
                        input.copyTo(output)
                    }
                }
                details.clear()
                details.put(MediaStore.MediaColumns.IS_PENDING, 0)
                val rows = resolver.update(uri, details, null, null)
                publishOk = rows > 0
            } catch (_: Exception) {
                publishOk = false
            }

            if (!publishOk) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext false
            }

            // Only now is it safe to remove the encrypted source.
            deleteMedia(file)
            true
        } catch (_: Exception) {
            // Stack frames intentionally not logged: they would leak vault paths.
            false
        }
    }

    suspend fun deleteMedia(file: File) {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
            val meta = File(file.parent, "${file.name}.meta")
            if (meta.exists()) meta.delete()
            val name = File(file.parent, "${file.name}.name")
            if (name.exists()) name.delete()
        }
    }

    private suspend fun saveOriginalName(mediaFile: File, originalName: String) {
        val nameFile = File(mediaFile.parent, "${mediaFile.name}.name")
        try {
            encryptToFile(originalName.toByteArray(Charsets.UTF_8), nameFile)
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
        }
    }

    suspend fun loadOriginalName(mediaFile: File): String? = withContext(Dispatchers.IO) {
        val nameFile = File(mediaFile.parent, "${mediaFile.name}.name")
        if (!nameFile.exists()) return@withContext null
        try {
            getDecryptedBytes(nameFile)?.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun isVideo(file: File): Boolean {
        return file.extension.lowercase() in VIDEO_EXTENSIONS
    }

    fun isAudio(file: File): Boolean {
        return file.extension.lowercase() in listOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "wma")
    }

    fun isDocument(file: File): Boolean {
        return file.extension.lowercase() in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv")
    }

    fun isMotionPhoto(file: File): Boolean {
        return File(file.parent, "${file.name}.meta").exists()
    }

    private suspend fun saveMetadata(mediaFile: File, motionInfo: MotionPhotoParser.MotionPhotoInfo) {
        val metaFile = File(mediaFile.parent, "${mediaFile.name}.meta")
        val content = "v=1;offset=${motionInfo.videoOffset}".toByteArray()
        try {
            encryptToFile(content, metaFile)
        } catch (e: Exception) {
            // printStackTrace removed: stack frames could leak vault file paths to logcat
        }
    }

    suspend fun loadMetadata(mediaFile: File): MotionPhotoParser.MotionPhotoInfo? {
        val metaFile = File(mediaFile.parent, "${mediaFile.name}.meta")
        if (!metaFile.exists()) return null

        return try {
            val content = getDecryptedBytes(metaFile)?.toString(Charsets.UTF_8)
            val offset = content?.substringAfter("offset=")?.toLongOrNull() ?: 0L
            MotionPhotoParser.MotionPhotoInfo(offset, 0, 0)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Stream the embedded MP4 trailer of a motion photo into a temp file, skipping
     * the leading JPEG bytes via the decryption stream rather than buffering the
     * whole file in memory. The temp file lands inside `cacheDir/decrypted/` so it
     * is reachable through `FileProvider` (file_paths.xml) AND swept by
     * [cleanupTempFiles].
     */
    suspend fun getMotionPhotoVideoTempFile(file: File): File? = withContext(Dispatchers.IO) {
        val info = loadMetadata(file) ?: return@withContext null
        try {
            val decryptedDir = File(context.cacheDir, "decrypted").apply { mkdirs() }
            val tempFile = File.createTempFile("hida_motion_", ".mp4", decryptedDir)
            tempFile.deleteOnExit()
            getDecryptedStream(file).use { input ->
                var skipped = 0L
                val skipBuf = ByteArray(64 * 1024)
                while (skipped < info.videoOffset) {
                    val n = input.read(
                        skipBuf,
                        0,
                        minOf(skipBuf.size.toLong(), info.videoOffset - skipped).toInt(),
                    )
                    if (n == -1) return@withContext null
                    skipped += n
                }
                FileOutputStream(tempFile).use { fos -> input.copyTo(fos) }
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clean up temp files from cache directory.
     */
    fun cleanupTempFiles() {
        // The decrypted/ subdir is exposed by FileProvider and holds every plaintext
        // temp file we generate. Wipe its contents in full so we don't accidentally
        // leave a renamed/legacy-prefixed file behind on lock.
        val decryptedDir = File(context.cacheDir, "decrypted")
        if (decryptedDir.exists()) {
            decryptedDir.listFiles()?.forEach { it.delete() }
        }
        // Belt-and-braces sweep at cacheDir root for any legacy-prefix leftovers
        // (older builds wrote `motion_video*` and `hida_*` directly under cacheDir).
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && (
                file.name.startsWith("hida_") ||
                file.name.startsWith("motion_video") ||
                file.name.startsWith("DocumentPicker")
            )) {
                file.delete()
            }
        }
    }
}

/**
 * InputStream wrapper for chunked decrypted data.
 */
class ChunkedDecryptionInputStream(
    private val file: File,
    private val cryptoManager: CryptoManager
) : InputStream() {

    private val raf = RandomAccessFile(file, "r")
    private val nonce: ByteArray
    private var currentChunkIndex = 0L
    private var currentChunkData: ByteArray? = null
    private var currentChunkOffset = 0
    private var isEof = false

    init {
        nonce = ByteArray(CryptoManager.NONCE_SIZE)
        raf.read(nonce)
    }

    override fun read(): Int {
        if (isEof) return -1

        if (currentChunkData == null || currentChunkOffset >= currentChunkData!!.size) {
            if (!loadNextChunk()) {
                isEof = true
                return -1
            }
        }

        return currentChunkData!![currentChunkOffset++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isEof) return -1
        if (len == 0) return 0

        var totalRead = 0
        var offset = off
        var remaining = len

        while (remaining > 0) {
            if (currentChunkData == null || currentChunkOffset >= currentChunkData!!.size) {
                if (!loadNextChunk()) {
                    isEof = true
                    break
                }
            }

            val chunk = currentChunkData!!
            val available = chunk.size - currentChunkOffset
            val toCopy = minOf(remaining, available)

            System.arraycopy(chunk, currentChunkOffset, b, offset, toCopy)
            currentChunkOffset += toCopy
            offset += toCopy
            remaining -= toCopy
            totalRead += toCopy
        }

        return if (totalRead == 0 && isEof) -1 else totalRead
    }

    private fun loadNextChunk(): Boolean {
        if (raf.filePointer >= raf.length()) return false

        val remaining = raf.length() - raf.filePointer
        val encryptedChunkSize = minOf(CryptoManager.ENCRYPTED_CHUNK_SIZE.toLong(), remaining).toInt()

        if (encryptedChunkSize <= CryptoManager.AUTH_TAG_SIZE) return false

        val encryptedChunk = ByteArray(encryptedChunkSize)
        raf.read(encryptedChunk)

        currentChunkData = cryptoManager.decryptChunk(encryptedChunk, nonce, currentChunkIndex)
        currentChunkIndex++
        currentChunkOffset = 0
        return true
    }

    override fun close() {
        raf.close()
    }
}
