package com.example.droneservicesapp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 enforcement: the UI is Compose-only. These checks fail the build if XML layouts,
 * menus, navigation graphs, View Binding, Fragments or the removed View libraries come back.
 */
class LegacyUiEnforcementTest {
    private val moduleDir = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val sourceSets = File(moduleDir, "src").listFiles().orEmpty().filter { it.isDirectory }

    @Test
    fun noLayoutMenuOrNavigationResources() {
        val legacy = sourceSets.flatMap { set ->
            File(set, "res").listFiles().orEmpty()
                .filter { dir -> dir.isDirectory && dir.name.substringBefore('-') in setOf("layout", "menu", "navigation") }
                .filter { dir -> dir.listFiles().orEmpty().isNotEmpty() }
                .map { it.relativeTo(moduleDir).path }
        }
        assertTrue("XML UI resources are not allowed: $legacy", legacy.isEmpty())
    }

    @Test
    fun viewBindingAndRemovedViewLibrariesStayOff() {
        val gradle = File(moduleDir, "build.gradle").readText()
        assertFalse("View Binding must stay disabled", Regex("""viewBinding\s*(=\s*)?true""").containsMatchIn(gradle))
        val removed = listOf(
            "com.google.android.material:material",
            "androidx.constraintlayout:constraintlayout",
            "androidx.recyclerview:recyclerview",
            "androidx.fragment:fragment-ktx",
            "androidx.navigation:navigation-fragment",
            "androidx.navigation:navigation-ui",
        )
        val declared = removed.filter { gradle.contains("\"$it") || gradle.contains("'$it") }
        assertTrue("Removed View-era dependencies are declared again: $declared", declared.isEmpty())
    }

    @Test
    fun codeUsesNoFragmentsOrViewWidgetLibraries() {
        val forbidden = listOf(
            "import androidx.fragment.",
            "import androidx.constraintlayout.",
            "import androidx.recyclerview.",
            "import com.google.android.material.",
            "import androidx.viewbinding.",
            "import androidx.databinding.",
            "import android.app.AlertDialog",
            "import android.app.ProgressDialog",
            "import android.app.Dialog",
        )
        val offenders = sourceSets.flatMap { set ->
            File(set, "java").walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter { file -> file.readLines().any { line -> forbidden.any { line.trimStart().startsWith(it) } } }
                .map { it.relativeTo(moduleDir).path }
                .toList()
        }
        assertTrue("View-era imports found: $offenders", offenders.isEmpty())
    }

    @Test
    fun offlineDownloadsShowProgressInComposeNotOsmdroidsDialog() {
        // CacheManager.downloadAreaAsync(...) opens osmdroid's own View ProgressDialog;
        // the app must use downloadAreaAsyncNoUI and show progress in Compose.
        val productionSets = sourceSets.filterNot { it.name.startsWith("test") || it.name.startsWith("androidTest") }
        val offenders = productionSets.flatMap { set ->
            File(set, "java").walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter { file -> Regex("""\.downloadAreaAsync\s*\(""").containsMatchIn(file.readText()) }
                .map { it.relativeTo(moduleDir).path }
                .toList()
        }
        assertTrue("osmdroid's progress dialog is used by: $offenders", offenders.isEmpty())
    }
}
