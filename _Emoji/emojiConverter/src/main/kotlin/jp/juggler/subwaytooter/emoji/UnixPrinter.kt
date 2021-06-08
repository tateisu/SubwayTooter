package jp.juggler.subwaytooter.emoji

import java.io.*

class UnixPrinter(file: File) : AutoCloseable {

	companion object {
		const val lineFeed = "\u000a"
	}

	private val writer = OutputStreamWriter(BufferedOutputStream(FileOutputStream(file)), Charsets.UTF_8)

	override fun close() {
		writer.flush()
		writer.close()
	}

	private fun print(x: String) {
		writer.write(x, 0, x.length)
	}

	fun println(x: String) {
		print(x)
		print(lineFeed)
	}
}
