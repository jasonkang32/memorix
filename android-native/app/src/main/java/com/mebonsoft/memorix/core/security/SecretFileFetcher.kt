package com.mebonsoft.memorix.core.security

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import java.io.File
import okio.Buffer

/**
 * `.mrxsecret` 파일을 메모리에서 복호화해 Coil에 넘긴다. 평문을 디스크에 남기지 않는다.
 * 보호 항목의 미리보기를 가릴지는 화면(잠금 여부·인증 상태)이 정한다.
 */
class SecretFileFetcher(
    private val file: File,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val plain = SecretMediaCrypto.decrypt(context, file.readBytes())
        return SourceResult(
            source = ImageSource(Buffer().write(plain), context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (SecretMediaPathSupport.isEncryptedPath(data.path)) SecretFileFetcher(data, context) else null
    }
}
