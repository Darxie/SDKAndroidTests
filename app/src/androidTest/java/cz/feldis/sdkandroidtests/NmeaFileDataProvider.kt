package cz.feldis.sdkandroidtests

import android.content.Context
import com.sygic.sdk.route.simulator.NmeaDataProvider
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.Queue

class NmeaFileDataProvider(private val context: Context, private val assetFileName: String) : NmeaDataProvider {
    private val sentences: Queue<String> = LinkedList()

    init {
        loadNmeaFile()
    }

    private fun loadNmeaFile() {
        try {
            val inputStream = context.assets.open(assetFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null) {
                // Ensure the line is a valid NMEA sentence before adding
                if (line.startsWith("$")) {
                    sentences.add(line)
                }
                line = reader.readLine()
            }

            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getNmeaSentence(): String {
        return sentences.poll() ?: ""
    }
}