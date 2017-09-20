package jp.juggler.subwaytooter.util;

public abstract  class WorkerBase extends Thread{

	private static final LogCategory log = new LogCategory( "WorkerBase" );

	public abstract void cancel();
	public abstract void run();
	
	public void notifyEx(){
		try{
			synchronized( this ){
				notify();
			}
		}catch(Throwable ex){
			log.trace(ex);
		}
	}
	
	
	public void waitEx( long ms ){
		try{
			synchronized( this ){
				wait( ms );
			}
		}catch(Throwable ex){
			log.trace(ex);
		}
	}
}
