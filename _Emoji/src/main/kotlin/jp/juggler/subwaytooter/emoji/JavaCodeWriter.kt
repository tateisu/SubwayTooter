package jp.juggler.subwaytooter.emoji

import java.io.PrintWriter

class JavaCodeWriter(private val writer: PrintWriter) {

	private var linesInFunction = 0
	private var functionsCount = 0

	fun addCode(code: String) {
		// open new function
		if (linesInFunction == 0) {
			++functionsCount
			writer.println("\n\tprivate static void init$functionsCount(){")
		}
		// write code
		writer.print("\t\t")
		writer.println(code)

		//  close function
		if (++linesInFunction > 100) {
			writer.println("\t}")
			linesInFunction = 0
		}
	}

	fun closeFunction() {
		if (linesInFunction > 0) {
			writer.println("\t}")
			linesInFunction = 0
		}
	}

	fun writeDefinition(s: String) {
		writer.println("\t$s")
		writer.println("")
	}

	fun writeInitializer() {
		writer.println("\tstatic void initAll(){")
		for (i in 1..functionsCount) {
			writer.println("\t\tinit$i();")
		}
		writer.println("\t}")
	}
}
