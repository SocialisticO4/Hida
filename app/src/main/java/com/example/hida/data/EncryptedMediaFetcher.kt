package com.example.hida.data

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.hida.data.MediaRepository
import okio.buffer
import okio.source
import java.io.File

class EncryptedMediaFetcher(
    private val file: File,
    private val repository: MediaRepository,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): SourceResult {
        val inputStream = repository.getDecryptedStream(file)
        val bufferedSource = inputStream.source().buffer()
        
        return SourceResult(
            source = coil.decode.ImageSource(
                source = bufferedSource,
                context = context
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val repository: MediaRepository) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle files in the secure directory
            if (data.parentFile?.name == "secure_media") {
                return EncryptedMediaFetcher(data, repository, options.context)
            }
            return null
        }
    }
}
