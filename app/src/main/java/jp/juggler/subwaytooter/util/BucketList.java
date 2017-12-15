package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

public class BucketList < E >
	extends AbstractList< E >
	implements Iterable< E >, RandomAccess
{
	
	private static final int DEFAULT_BUCKET_CAPACITY = 1024;
	
	@SuppressWarnings("WeakerAccess")
	public BucketList( int initialStep ){
		mStep = initialStep;
	}
	
	@SuppressWarnings("WeakerAccess")
	public BucketList(){
		this( DEFAULT_BUCKET_CAPACITY );
	}
	
	private static class Bucket < E > extends ArrayList< E > {
		int total_start;
		int total_end;
		
		Bucket( int capacity ){
			super( capacity );
		}
	}
	
	private final ArrayList< Bucket< E > > groups = new ArrayList<>();
	private int mSize;
	private final int mStep;
	
	private void updateIndex(){
		int n = 0;
		for( Bucket< E > bucket : groups ){
			bucket.total_start = n;
			bucket.total_end = n = n + bucket.size();
		}
		mSize = n;
	}
	
	private static class BucketPos {
		int group_index;
		int bucket_index;
		
		BucketPos(){
		}
		
		BucketPos( int gi, int bi ){
			this.group_index = gi;
			this.bucket_index = bi;
		}
	}
	
	private static final ThreadLocal< BucketPos > pos_internal = new ThreadLocal< BucketPos >() {
		@Override protected BucketPos initialValue(){
			return new BucketPos();
		}
	};
	
	private BucketPos findPos( BucketPos dst, int total_index ){
		
		if( total_index < 0 || total_index >= mSize ){
			throw new ArrayIndexOutOfBoundsException( "findPos: bad index=" + total_index + ", size=" + mSize );
		}
		
		// binary search
		int gs = 0;
		int ge = groups.size();
		for( ; ; ){
			int gi = ( gs + ge ) >> 1;
			Bucket< E > group = groups.get( gi );
			if( total_index < group.total_start ){
				ge = gi;
			}else if( total_index >= group.total_end ){
				gs = gi + 1;
			}else{
				if( dst == null ) dst = new BucketPos();
				dst.group_index = gi;
				dst.bucket_index = total_index - group.total_start;
				return dst;
			}
		}
	}
	
	@Override
	public void clear(){
		groups.clear();
		mSize = 0;
	}
	
	// 末尾への追加
	@Override
	public boolean addAll( @NonNull Collection< ? extends E > c ){
		int c_size = c.size();
		if( c_size == 0 ) return false;
		
		// 最後のバケツに収まるなら、最後のバケツの中に追加する
		if( groups.size() > 0 ){
			Bucket< E > bucket = groups.get( groups.size() - 1 );
			if( bucket.size() + c_size <= mStep ){
				bucket.addAll( c );
				bucket.total_end += c_size;
				mSize += c_size;
				return true;
			}
		}
		// 新しいバケツを作って、そこに追加する
		Bucket< E > bucket = new Bucket<>( mStep );
		bucket.addAll( c );
		bucket.total_start = mSize;
		bucket.total_end = mSize + c_size;
		mSize += c_size;
		groups.add( bucket );
		return true;
	}
	
	@Override
	public boolean addAll( int index, @NonNull Collection< ? extends E > c ){
		
		// indexが終端なら、終端に追加する
		// バケツがカラの場合もここ
		if( index == mSize ){
			return addAll( c );
		}
		
		int c_size = c.size();
		if( c_size == 0 ) return false;
		
		BucketPos pos = findPos( pos_internal.get(), index );
		Bucket< E > bucket = groups.get( pos.group_index );
		
		// 挿入位置がバケツの先頭ではないか、バケツのサイズに問題がないなら
		if( pos.bucket_index > 0 || bucket.size() + c_size <= mStep ){
			// バケツの中に挿入する
			bucket.addAll( pos.bucket_index, c );
		}else{
			// 新しいバケツを作って、そこに追加する
			bucket = new Bucket<>( mStep );
			bucket.addAll( c );
			groups.add( pos.group_index, bucket );
		}
		
		updateIndex();
		return true;
	}
	
	public E remove( int index ){
		BucketPos pos = findPos( pos_internal.get(), index );
		
		Bucket< E > bucket = groups.get( pos.group_index );
		
		E data = bucket.remove( pos.bucket_index );
		
		if( bucket.isEmpty() ){
			groups.remove( pos.group_index );
		}
		
		updateIndex();
		return data;
	}
	
	public int size(){
		return mSize;
	}
	
	public boolean isEmpty(){
		return 0 == mSize;
	}
	
	public E get( int idx ){
		BucketPos pos = findPos( pos_internal.get(), idx );
		return groups.get( pos.group_index ).get( pos.bucket_index );
	}
	
	private class MyIterator implements Iterator< E > {
		private final BucketPos pos; // indicates next read point
		
		MyIterator(){
			pos = new BucketPos( 0, 0 );
		}
		
		@Override public boolean hasNext(){
			for( ; ; ){
				if( pos.group_index >= groups.size() ){
					return false;
				}
				Bucket< E > bucket = groups.get( pos.group_index );
				if( pos.bucket_index >= bucket.size() ){
					pos.bucket_index = 0;
					++ pos.group_index;
					continue;
				}
				return true;
			}
		}
		
		@Override public E next(){
			for( ; ; ){
				if( pos.group_index >= groups.size() ){
					throw new NoSuchElementException();
				}
				Bucket< E > bucket = groups.get( pos.group_index );
				if( pos.bucket_index >= bucket.size() ){
					pos.bucket_index = 0;
					++ pos.group_index;
					continue;
				}
				return bucket.get( pos.bucket_index++ );
			}
		}
		
		@Override public void remove(){
			throw new UnsupportedOperationException();
		}
	}
	
	@NonNull @Override public Iterator< E > iterator(){
		return new MyIterator();
	}
	
}
