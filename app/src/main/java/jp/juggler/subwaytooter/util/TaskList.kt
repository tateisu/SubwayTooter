package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.*

class TaskList {
	
	companion object {
		
		private val log = LogCategory("TaskList")
		private const val FILE_TASK_LIST = "JOB_TASK_LIST"
	}
	
	private lateinit var _list : LinkedList<JsonObject>
	
	@Synchronized
	private fun prepareList(context : Context) : LinkedList<JsonObject> {
		if(! ::_list.isInitialized) {
			_list = LinkedList()
			
			try {
				context.openFileInput(FILE_TASK_LIST).use { inputStream ->
					val bao = ByteArrayOutputStream()
					IOUtils.copy(inputStream, bao)
					bao.toByteArray().decodeUTF8().decodeJsonArray().toObjectList().forEach {
						_list.add(it)
					}
				}
			} catch(ex : FileNotFoundException) {
				log.e(ex, "prepareList: file not found.")
			} catch(ex : Throwable) {
				log.trace(ex, "TaskList: prepareArray failed.")
			}
		}
		
		return _list
	}
	
	@Synchronized
	private fun saveArray(context : Context) {
		val list = prepareList(context)
		try {
			log.d("saveArray size=%s", list.size)
			val data = JsonArray(list).toString().encodeUTF8()
			context.openFileOutput(FILE_TASK_LIST, Context.MODE_PRIVATE)
				.use { IOUtils.write(data, it) }
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "TaskList: saveArray failed.size=%s", list.size)
		}
		
	}
	
	@Synchronized
	fun addLast(context : Context, removeOld : Boolean, taskData : JsonObject) {
		val list = prepareList(context)
		if(removeOld) {
			val it = list.iterator()
			while(it.hasNext()) {
				val item = it.next()
				if(taskData == item) it.remove()
			}
		}
		list.addLast(taskData)
		saveArray(context)
	}
	
	@Suppress("unused")
	@Synchronized
	fun hasNext(context : Context) : Boolean {
		return prepareList(context).isNotEmpty()
	}
	
	@Synchronized
	fun next(context : Context) : JsonObject? {
		val list = prepareList(context)
		val item = if(list.isEmpty()) null else list.removeFirst()
		saveArray(context)
		return item
	}
	
}
