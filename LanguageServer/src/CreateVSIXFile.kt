import me.anno.zauber.langserver.logging.AppendLogging
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * pack the .jar and everything from vscode folder into a zip
 * */
fun main() {
    // must be created using "vsce package" run in the vscode folder:
    val src = File("./LanguageServer/vscode/zauber-0.0.1.vsix")
    val dst = File("./out/artifacts/ZauberLanguageServer/ZauberLanguageServer.vsix")
    val jar = File("./out/artifacts/ZauberLanguageServer/ZauberLanguageServer.jar")

    ZipOutputStream(dst.outputStream().buffered()).use { zos ->
        ZipInputStream(src.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                zos.putNextEntry(entry)
                zis.copyTo(zos)
                zos.closeEntry()
            }
        }

        zos.putNextEntry(ZipEntry("extension/server/${jar.name}"))
        ZipInputStream(jar.inputStream().buffered()).use { zis2 ->
            val tmp = ByteArrayOutputStream(jar.length().toInt())
            ZipOutputStream(tmp).use { zos2 ->
                while (true) {
                    val entry = zis2.nextEntry ?: break
                    if (entry.name.startsWith("META-INF") &&
                        (entry.name.endsWith(".SF") ||
                                entry.name.endsWith(".RSA") ||
                                entry.name.endsWith(".DSA"))
                    ) continue
                    zos2.putNextEntry(ZipEntry(entry.name))
                    zis2.copyTo(zos2)
                    zos2.closeEntry()
                }
            }
            zos.write(tmp.toByteArray())
        }
        zos.closeEntry()
    }
    println("Created $dst, ${"%.2f".format(dst.length() / 1e6f)} MB")
    AppendLogging.info("Created $dst")
}
