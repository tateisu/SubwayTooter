//package jp.juggler.util.os
//
//import android.database.Cursor
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import jp.juggler.util.coroutine.AppDispatchers
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class WatchCursor(
//    val activity:AppCompatActivity,
//    // カーソル作成処理。ワーカースレッドで動く
//    val creator:suspend ()-> Cursor?,
//    // カーソルが更新されたら呼び出される
//    var onChanged:()->Unit,
//) :AutoCloseable{
//    private var cursor:Cursor? = null
//
//    fun switchCursor() {
//        cursor?.unregisterContentObserver(this)
//        registerContentObserver(ContentObserver observer)
//
//    }
//
//
//    suspend fun startQuery(){
//        activity.lifecycleScope.launch {
//            try {
//                val newCursor = withContext(AppDispatchers.IO) {
//                    creator()
//                }
//                switchCursor()
//            }catch(ex:Throwable){
//                log.e(ex,"startQuery failed.")
//            }
//            }
//        }
//    }
//
//
//    init{
//
//    }
//    suspend fun initSuspend(){
//        startQuery()
//    }
//}
//
//suspend fun watchCursor(creator:()-> Cursor?){
//    return WatchCursor(creator).apply{ initSuspend() }
//}
