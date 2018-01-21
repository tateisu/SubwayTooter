package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;

// このクラスをkotlinに変換しないこと
// Kotlin は wait/notify をサポートしてない
// しかしConcurrent ライブラリには notify() を直接表現できるクラスがない
// 仕方がないのでJavaコード経由でwait/notifyを呼び出す

@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class WaitNotifyHelper {
	
	public static void waitEx( @NonNull Object obj, long ms ){
		try{
			synchronized( obj ){
				obj.wait( ms );
			}
		}catch( InterruptedException ignored ){
		}
	}
	
	public static void notifyEx( @NonNull Object obj ){
		try{
			synchronized( obj ){
				obj.notify();
			}
		}catch( Throwable ignored ){
		}
		
	}
	
}
