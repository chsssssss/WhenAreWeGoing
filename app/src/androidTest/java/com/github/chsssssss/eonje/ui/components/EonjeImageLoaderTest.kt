package com.github.chsssssss.eonje.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EonjeImageLoaderTest {

    @Test
    fun 디스크_캐시_상한이_50MB로_고정된다() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val cache = EonjeImageLoader.build(context).diskCache

        assertNotNull(cache)
        assertEquals(50L * 1024 * 1024, cache!!.maxSize)
        assertEquals("Coil 기본 위치와 같아야 기존 캐시를 이어 쓴다", "image_cache", cache.directory.toFile().name)
    }
}
