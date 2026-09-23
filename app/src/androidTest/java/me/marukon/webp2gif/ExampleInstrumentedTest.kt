/*
 * 文件职责：安卓底层集成测试，验证应用的 Context 包名。
 * 主要内容：验证运行时的 packageName 是否正确。
 * 依赖：无
 */
package me.marukon.webp2gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 获取测试上下文对象
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("me.marukon.webp2gif", appContext.packageName)
    }
}
