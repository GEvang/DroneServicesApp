package com.example.droneservicesapp.data.terrain

import android.content.Context
import android.net.Network
import com.example.droneservicesapp.domain.terrain.SrtmTileId
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SrtmPreparationProgress(
    val completedTiles: Int,
    val totalTiles: Int,
    val currentTile: SrtmTileId?,
    val usingCache: Boolean,
    val overallPercent: Int = if (totalTiles > 0) completedTiles * 100 / totalTiles else 0,
)

interface TerrainSourceRepository {
    suspend fun prepare(
        plan: TerrainCoveragePlan,
        onProgress: (SrtmPreparationProgress) -> Unit = {},
    ): TerrainElevationSource
}

class SrtmTerrainRepository(
    context: Context,
    private val internetNetworkProvider: () -> Network?,
) : TerrainSourceRepository {
    private val appContext = context.applicationContext

    override suspend fun prepare(
        plan: TerrainCoveragePlan,
        onProgress: (SrtmPreparationProgress) -> Unit,
    ): TerrainElevationSource = withContext(Dispatchers.IO) {
        val orderedTiles = plan.sourceTiles.sortedWith(compareBy({ it.southLatitude }, { it.westLongitude }))
        val loaded = LinkedHashMap<SrtmTileId, SrtmHgtTile>()
        try {
            orderedTiles.forEachIndexed { index, tileId ->
                val file = cachedFile(tileId)
                val cached = isValidTile(file)
                onProgress(SrtmPreparationProgress(index, orderedTiles.size, tileId, cached))
                if (!cached) {
                    var lastReportedPercent = -1
                    downloadAndExtract(tileId, file) { extractedBytes ->
                        val tileFraction = extractedBytes.toDouble() / SrtmHgtTile.SRTM1_FILE_BYTES
                        val overallPercent = (
                            (index + tileFraction.coerceIn(0.0, 1.0)) * 100.0 / orderedTiles.size
                        ).toInt().coerceIn(0, 99)
                        if (overallPercent != lastReportedPercent) {
                            lastReportedPercent = overallPercent
                            onProgress(
                                SrtmPreparationProgress(
                                    completedTiles = index,
                                    totalTiles = orderedTiles.size,
                                    currentTile = tileId,
                                    usingCache = false,
                                    overallPercent = overallPercent,
                                )
                            )
                        }
                    }
                }
                loaded[tileId] = SrtmHgtTile(tileId, file)
                onProgress(
                    SrtmPreparationProgress(
                        index + 1,
                        orderedTiles.size,
                        tileId,
                        cached,
                        overallPercent = (index + 1) * 100 / orderedTiles.size,
                    )
                )
            }
            PreparedTerrainSource(plan, loaded)
        } catch (error: Throwable) {
            loaded.values.forEach(SrtmHgtTile::close)
            throw error
        }
    }

    internal fun cachedFile(tileId: SrtmTileId): File =
        File(File(appContext.filesDir, CACHE_DIRECTORY), "${tileId.baseName}.hgt")

    private fun downloadAndExtract(
        tileId: SrtmTileId,
        destination: File,
        onExtractProgress: (Long) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        val url = URL(tileId.downloadUrl)
        val network = internetNetworkProvider()
        val connection = ((network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/zip, application/octet-stream")
            setRequestProperty("User-Agent", "DroneServicesApp/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Terrain source returned HTTP ${connection.responseCode} for ${tileId.baseName}")
            }
            ZipInputStream(BufferedInputStream(connection.inputStream)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && !entry.name.substringAfterLast('/').equals("${tileId.baseName}.hgt", true)) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                require(entry != null) { "Terrain archive does not contain ${tileId.baseName}.hgt" }
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var extractedBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        extractedBytes += count
                        onExtractProgress(extractedBytes)
                    }
                }
            }
            require(isValidTile(temporary)) { "Invalid SRTM1 tile size for ${tileId.baseName}" }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun isValidTile(file: File): Boolean = file.isFile && file.length() == SrtmHgtTile.SRTM1_FILE_BYTES

    companion object {
        private const val CACHE_DIRECTORY = "terrain/srtm1"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
