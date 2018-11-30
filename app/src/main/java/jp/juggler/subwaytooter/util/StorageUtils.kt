@file:Suppress("unused")

package jp.juggler.subwaytooter.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import java.io.File
import java.util.*

object StorageUtils{
	
	private val log = LogCategory("StorageUtils")
	
	private const val PATH_TREE = "tree"
	private const val PATH_DOCUMENT = "document"
	
	internal class FileInfo(any_uri : String?) {
		
		var uri : Uri? = null
		private var mime_type : String? = null
		
		init {
			if(any_uri != null) {
				uri = if(any_uri.startsWith("/")) {
					Uri.fromFile(File(any_uri))
				} else {
					any_uri.toUri()
				}
				val ext = MimeTypeMap.getFileExtensionFromUrl(any_uri)
				if(ext != null) {
					mime_type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase())
				}
			}
		}
	}
	
	private fun getSecondaryStorageVolumesMap(context : Context) : Map<String, String> {
		val result = HashMap<String, String>()
		try {
			val sm = context.applicationContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
			if(sm == null) {
				log.e("can't get StorageManager")
			} else {
				
				// SDカードスロットのある7.0端末が手元にないから検証できない
				//			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ){
				//				for(StorageVolume volume : sm.getStorageVolumes() ){
				//					// String path = volume.getPath();
				//					String state = volume.getState();
				//
				//				}
				//			}
				
				val getVolumeList = sm.javaClass.getMethod("getVolumeList")
				val volumes = getVolumeList.invoke(sm)
				log.d("volumes type=%s", volumes.javaClass)
				
				if(volumes is ArrayList<*>) {
					//
					for(volume in volumes) {
						val volume_clazz = volume.javaClass
						
						val path = volume_clazz.getMethod("getPath").invoke(volume) as? String
						val state = volume_clazz.getMethod("getState").invoke(volume) as? String
						if(path != null && state == "mounted") {
							//
							val isPrimary = volume_clazz.getMethod("isPrimary").invoke(volume) as? Boolean
							if(isPrimary == true) result["primary"] = path
							//
							val uuid = volume_clazz.getMethod("getUuid").invoke(volume) as? String
							if(uuid != null) result[uuid] = path
						}
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return result
	}
	
	private fun isExternalStorageDocument(uri : Uri) : Boolean {
		return "com.android.externalstorage.documents" == uri.authority
	}
	
	private fun getDocumentId(documentUri : Uri) : String {
		val paths = documentUri.pathSegments
		if(paths.size >= 2 && PATH_DOCUMENT == paths[0]) {
			// document
			return paths[1]
		}
		if(paths.size >= 4 && PATH_TREE == paths[0]
			&& PATH_DOCUMENT == paths[2]) {
			// document in tree
			return paths[3]
		}
		if(paths.size >= 2 && PATH_TREE == paths[0]) {
			// tree
			return paths[1]
		}
		throw IllegalArgumentException("Invalid URI: $documentUri")
	}
	
	fun getFile(context : Context, path : String) : File? {
		try {
			if(path.startsWith("/")) return File(path)
			val uri = path.toUri()
			if("file" == uri.scheme) return File(uri.path)
			
			// if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
			run {
				if(isExternalStorageDocument(uri)) {
					try {
						val docId = getDocumentId(uri)
						val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
						if(split.size >= 2) {
							val uuid = split[0]
							if("primary".equals(uuid, ignoreCase = true)) {
								return File(Environment.getExternalStorageDirectory().toString() + "/" + split[1])
							} else {
								val volume_map = getSecondaryStorageVolumesMap(context)
								val volume_path = volume_map[uuid]
								if(volume_path != null) {
									return File(volume_path + "/" + split[1])
								}
							}
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			// MediaStore Uri
			context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
				if(cursor.moveToFirst()) {
					val col_count = cursor.columnCount
					for(i in 0 until col_count) {
						val type = cursor.getType(i)
						if(type != Cursor.FIELD_TYPE_STRING) continue
						val name = cursor.getColumnName(i)
						val value = cursor.getStringOrNull(i)
						if(value != null && value.isNotEmpty() && "filePath" == name) return File(value)
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
}