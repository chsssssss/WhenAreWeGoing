package com.github.chsssssss.eonje.ui.components

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coil 이미지 로더 설정. 디스크 캐시 상한을 코드로 고정해 둔다.
 *
 * 기본값(여유 공간의 2%, 최대 250MB)을 그대로 두면 상한이 기기마다 달라진다. 썸네일은 대부분 로컬 파일(file://)이라
 * 이 캐시를 거의 쓰지 않고, 다운로드가 실패해 원격 URL이 저장된 경우에만 쓰인다 — 그래서 상한은 작게 잡는다.
 * 캐시 폴더는 Coil 기본 위치(cacheDir/image_cache)와 같게 둬서 이미 쌓인 캐시를 그대로 이어서 쓴다.
 */
object EonjeImageLoader {
    const val DISK_CACHE_MAX_BYTES = 50L * 1024 * 1024
    private const val DISK_CACHE_DIR = "image_cache"

    private val installed = AtomicBoolean(false)

    /** 프로세스에서 한 번만 설치한다. 첫 이미지 로드(setContent) 전에 호출해야 한다. */
    fun install(context: Context) {
        if (installed.compareAndSet(false, true)) {
            Coil.setImageLoader(build(context.applicationContext))
        }
    }

    internal fun build(context: Context): ImageLoader = ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIR))
                .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()
}
