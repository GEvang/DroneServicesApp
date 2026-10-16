package com.example.droneservicesapp.ui.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the Phase 2/7 rule: the map engine (osmdroid) stays behind the adapter in
 * `ui/map/osmdroid`, so screens, state and domain code only use the engine-independent models.
 */
class MapEngineBoundaryTest {
    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val adapterDir = File(sourceRoot, "com/example/droneservicesapp/ui/map/osmdroid")

    @Test
    fun osmdroidIsOnlyImportedByTheMapAdapter() {
        val offenders = sourceFiles()
            .filterNot { it.canonicalPath.startsWith(adapterDir.canonicalPath + File.separator) }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import org.osmdroid") } }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        assertTrue("osmdroid imported outside ui/map/osmdroid: $offenders", offenders.isEmpty())
    }

    @Test
    fun domainAndDataLayersStayIndependentOfTheMapAndCompose() {
        val base = File(sourceRoot, "com/example/droneservicesapp")
        val forbidden = listOf("import androidx.compose", "import org.osmdroid", "import com.example.droneservicesapp.ui.map")
        val offenders = listOf("domain", "data").flatMap { layer ->
            File(base, layer).walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter { file -> file.readLines().any { line -> forbidden.any { line.trimStart().startsWith(it) } } }
                .map { it.relativeTo(sourceRoot).path }
                .toList()
        }
        assertTrue("domain/data code depends on Compose or the map platform: $offenders", offenders.isEmpty())
    }

    private fun sourceFiles(): Sequence<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }
}
