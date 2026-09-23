package me.marukon.webp2gif

import org.junit.Test
import java.lang.reflect.Modifier

class VersionComparatorTest {

    @Test
    fun testReflection() {
        val sb = StringBuilder()
        sb.append("\n================ REFLECTION RESULTS ================\n")
        
        try {
            val gifEncoderClass = Class.forName("com.squareup.gifencoder.GifEncoder")
            sb.append("GifEncoder declaration: ${gifEncoderClass.name}\n")
            sb.append("GifEncoder interfaces: ${gifEncoderClass.interfaces.joinToString { it.name }}\n")
            gifEncoderClass.declaredMethods.forEach { method ->
                val modifiers = Modifier.toString(method.modifiers)
                sb.append("  $modifiers ${method.returnType.name} ${method.name}(${method.parameterTypes.joinToString { it.name }})\n")
            }
        } catch (e: Exception) {
            sb.append("Error reflecting GifEncoder: ${e.message}\n")
        }

        try {
            val imageOptionsClass = Class.forName("com.squareup.gifencoder.ImageOptions")
            sb.append("ImageOptions declaration: ${imageOptionsClass.name}\n")
            imageOptionsClass.declaredMethods.forEach { method ->
                val modifiers = Modifier.toString(method.modifiers)
                sb.append("  $modifiers ${method.returnType.name} ${method.name}(${method.parameterTypes.joinToString { it.name }})\n")
            }
        } catch (e: Exception) {
            sb.append("Error reflecting ImageOptions: ${e.message}\n")
        }
        
        sb.append("====================================================")
        println(sb.toString())
    }
}
