package jp.juggler.subwaytooter.emoji

import java.io.*

class JavaCodeWriter(file: File) : AutoCloseable {

	companion object {
		const val lineFeed = "\u000a"
	}

	private val writer = OutputStreamWriter(BufferedOutputStream(FileOutputStream(file)), Charsets.UTF_8)

	private var linesInFunction = 0
	private var functionsCount = 0

	override fun close() {
		writer.flush()
		writer.close()
	}

	fun print(x: String) {
		writer.write(x, 0, x.length)
	}

	fun println(x: String) {
		print(x)
		print(lineFeed)
		writer.flush()
	}


	fun addCode(code: String) {
		// open new function
		if (linesInFunction == 0) {
			++functionsCount
			println("\n\tprivate static void init$functionsCount(){")
		}
		// write code
		print("\t\t")
		println(code)

		//  close function
		if (++linesInFunction > 100) {
			println("\t}")
			linesInFunction = 0
		}
	}

	fun closeFunction() {
		if (linesInFunction > 0) {
			println("\t}")
			linesInFunction = 0
		}
	}

	fun writeDefinition(s: String) {
		println("\t$s")
		println("")
	}

	fun writeInitializer() {
		println("\tstatic void initAll(){")
		for (i in 1..functionsCount) {
			println("\t\tinit$i();")
		}
		println("\t}")
	}
}
