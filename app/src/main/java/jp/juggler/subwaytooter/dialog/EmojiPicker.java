package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.astuetz.PagerSlidingTabStrip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.util.CustomEmojiLister;
import jp.juggler.subwaytooter.util.EmojiMap201709;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.NetworkEmojiView;

@SuppressWarnings("WeakerAccess")
public class EmojiPicker implements View.OnClickListener, CustomEmojiLister.Callback {
	
	static final LogCategory log = new LogCategory( "EmojiPicker" );
	
	public interface Callback {
		void onPickedEmoji( String name );
	}
	
	public static void open(
		Activity activity
		, String instance
		, EmojiPicker.Callback callback
	){
		EmojiPicker picker = new EmojiPicker( activity, instance, callback );
		picker.show();
	}
	
	final Activity activity;
	final String instance;
	final Callback callback;
	final View viewRoot;
	final EmojiPickerPagerAdapter pager_adapter;
	final ArrayList< EmojiPickerPage > page_list = new ArrayList<>();
	final ViewPager pager;
	final Dialog dialog;
	final PagerSlidingTabStrip pager_strip;
	
	/////////////////////////////////////////////////////////////////////////////
	
	static class SkinTone {
		final int image_id;
		final String[] suffix_list;
		
		SkinTone( int image_id, String... suffix_list ){
			this.image_id = image_id;
			this.suffix_list = suffix_list;
		}
	}
	
	static SkinTone[] tone_list = new SkinTone[]{
		new SkinTone( R.drawable.emj_1f3fb, "_light_skin_tone", "_tone1" ),
		new SkinTone( R.drawable.emj_1f3fc, "_medium_light_skin_tone", "_tone2" ),
		new SkinTone( R.drawable.emj_1f3fd, "_medium_skin_tone", "_tone3" ),
		new SkinTone( R.drawable.emj_1f3fe, "_medium_dark_skin_tone", "_tone4" ),
		new SkinTone( R.drawable.emj_1f3ff, "_dark_skin_tone", "_tone5" ),
	};
	
	final ImageButton[] ibSkinTone = new ImageButton[ 5 ];
	
	int selected_tone;
	
	private void initSkinTone( int idx, int view_id ){
		ibSkinTone[ idx ] = viewRoot.findViewById( view_id );
		ibSkinTone[ idx ].setTag( tone_list[ idx ] );
		ibSkinTone[ idx ].setOnClickListener( this );
	}
	
	private void showSkinTone(){
		for( ImageButton ib : ibSkinTone ){
			if( selected_tone == ib.getId() ){
				ib.setImageResource( R.drawable.emj_2714 );
			}else{
				ib.setImageDrawable( null );
			}
		}
	}
	
	@Override public void onClick( View view ){
		int id = view.getId();
		if( selected_tone == id ){
			selected_tone = 0;
		}else{
			selected_tone = id;
		}
		showSkinTone();
	}
	
	private String applySkinTone( String name ){
		
		// Recentなどでは既にsuffixがついた名前が用意されている
		// suffixを除去する
		for( SkinTone tone : tone_list ){
			for( String suffix : tone.suffix_list ){
				if( name.endsWith( suffix ) ){
					name = name.substring( 0, name.length() - suffix.length() );
					break;
				}
			}
		}
		
		// 指定したトーンのサフィックスを追加して、絵文字が存在すればその名前にする
		SkinTone tone = (SkinTone) viewRoot.findViewById( selected_tone ).getTag();
		for( String suffix : tone.suffix_list ){
			String new_name = name + suffix;
			EmojiMap201709.EmojiInfo info  = EmojiMap201709.sShortNameToImageId.get( new_name );
			if( info != null ) return new_name;
		}
		return name;
	}
	
	/////////////////////////////////////////////////////////////////////////////
	
	static class EmojiItem {
		@NonNull final String name;
		@Nullable final String instance;
		
		EmojiItem( @NonNull final String name, @Nullable final String instance ){
			this.name = name;
			this.instance = instance;
		}
	}
	
	final ArrayList< EmojiItem > recent_list = new ArrayList<>();
	final ArrayList< EmojiItem > custom_list = new ArrayList<>();
	final HashMap< String, String > emoji_url_map = new HashMap<>();
	int recent_page_idx;
	int custom_page_idx;
	
	@SuppressLint("InflateParams")
	public EmojiPicker( @NonNull Activity activity, String instance, @NonNull Callback callback ){
		this.activity = activity;
		this.instance = instance;
		this.callback = callback;
		
		// recentをロードする
		SharedPreferences pref = App1.pref;
		String sv = pref.getString( Pref.KEY_EMOJI_PICKER_RECENT, null );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				JSONArray array = new JSONArray( sv );
				for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
					JSONObject item = array.optJSONObject( i );
					String c1 = Utils.optStringX( item, "name" );
					String c2 = Utils.optStringX( item, "instance" );
					if( ! TextUtils.isEmpty( c1 ) ){
						recent_list.add( new EmojiItem( c1, c2 ) );
					}
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		// create page
		this.recent_page_idx = page_list.size();
		page_list.add( new EmojiPickerPage( R.string.emoji_category_recent ) );
		this.custom_page_idx = page_list.size();
		page_list.add( new EmojiPickerPage( R.string.emoji_category_custom ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_people ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_nature ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_foods ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_activity ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_places ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_objects ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_symbols ) );
		page_list.add( new EmojiPickerPage( R.string.emoji_category_flags ) );
		
		this.viewRoot = activity.getLayoutInflater().inflate( R.layout.dlg_picker_emoji, null, false );
		this.pager = viewRoot.findViewById( R.id.pager );
		this.pager_strip = viewRoot.findViewById( R.id.pager_strip );
		
		initSkinTone( 0, R.id.btnSkinTone1 );
		initSkinTone( 1, R.id.btnSkinTone2 );
		initSkinTone( 2, R.id.btnSkinTone3 );
		initSkinTone( 3, R.id.btnSkinTone4 );
		initSkinTone( 4, R.id.btnSkinTone5 );
		showSkinTone();
		
		this.pager_adapter = new EmojiPickerPagerAdapter();
		pager.setAdapter( pager_adapter );
		pager_strip.setViewPager( pager );
		
		// カスタム絵文字をロードする
		if( ! TextUtils.isEmpty( instance ) ){
			CustomEmoji.List list = App1.custom_emoji_lister.get( instance, this );
			setCustomEmojiList( list );
		}
		
		this.dialog = new Dialog( activity );
		dialog.setContentView( viewRoot );
		dialog.setCancelable( true );
		dialog.setCanceledOnTouchOutside( true );
		dialog.getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE );
	}
	
	@Override public void onListLoadComplete( CustomEmoji.List list ){
		setCustomEmojiList( list );
	}
	
	private void setCustomEmojiList( @Nullable CustomEmoji.List list ){
		if( list == null ) return;
		custom_list.clear();
		for( CustomEmoji emoji : list ){
			custom_list.add( new EmojiItem( emoji.shortcode, instance ) );
			emoji_url_map.put( emoji.shortcode, emoji.url );
		}
		EmojiPickerPageViewHolder vh = pager_adapter.getPageViewHolder( custom_page_idx );
		if( vh != null ){
			vh.notifyDataSetChanged();
		}
		vh = pager_adapter.getPageViewHolder( recent_page_idx );
		if( vh != null ){
			vh.notifyDataSetChanged();
		}
	}
	
	void show(){
		dialog.show();
	}
	
	class EmojiPickerPage {
		final int title_id;
		@NonNull final String title;
		@NonNull final ArrayList< EmojiItem > emoji_list;
		
		EmojiPickerPage( int title_id ){
			this.title_id = title_id;
			this.title = activity.getString( title_id );
			EmojiMap201709.Category c = EmojiMap201709.sCategoryMap.get( title_id );
			if( c != null ){
				this.emoji_list = new ArrayList<>();
				for( String name : c.emoji_list ){
					this.emoji_list.add( new EmojiItem( name, null ) );
				}
			}else if( title_id == R.string.emoji_category_recent ){
				this.emoji_list = new ArrayList<>();
				for( EmojiItem item : recent_list ){
					if( item.instance != null && ! item.instance.equals( instance ) ) continue;
					this.emoji_list.add( item );
				}
			}else if( title_id == R.string.emoji_category_custom ){
				this.emoji_list = custom_list;
			}else{
				this.emoji_list = new ArrayList<>();
			}
		}
		
	}
	
	public class EmojiPickerPageViewHolder extends BaseAdapter implements AdapterView.OnItemClickListener {
		GridView gridView;
		EmojiPickerPage page;
		int wh;
		
		public EmojiPickerPageViewHolder( Activity activity, View root ){
			this.gridView = root.findViewById( R.id.gridView );
			gridView.setAdapter( this );
			gridView.setOnItemClickListener( this );
			
			this.wh = (int) ( 0.5f + 48f * activity.getResources().getDisplayMetrics().density );
		}
		
		public void onPageCreate( EmojiPickerPage page ){
			this.page = page;
		}
		
		public void onPageDestroy(){
			
		}
		
		@Override public int getCount(){
			if( page == null ) return 0;
			return page.emoji_list.size();
		}
		
		@Override public Object getItem( int i ){
			if( page == null ) return null;
			return page.emoji_list.get( i );
		}
		
		@Override public long getItemId( int i ){
			return 0;
		}
		
		@Override public int getViewTypeCount(){
			return 2;
		}
		
		@Override public int getItemViewType( int position ){
			if( page == null ) return 0;
			EmojiItem item = page.emoji_list.get( position );
			return item.instance == null ? 0 : 1;
		}
		
		@Override public View getView( int position, View view, ViewGroup viewGroup ){
			EmojiItem item = page.emoji_list.get( position );
			if( item.instance == null ){
				if( view == null ){
					view = new ImageView( activity );
					GridView.LayoutParams lp = new GridView.LayoutParams( wh, wh );
					view.setLayoutParams( lp );
				}
				view.setTag( item );
				ImageView iv = (ImageView) view;
				if( page != null ){
					EmojiMap201709.EmojiInfo info  = EmojiMap201709.sShortNameToImageId.get( item.name );
					if( info != null ){
						iv.setImageResource( info.image_id );
					}
				}
			}else{
				if( view == null ){
					view = new NetworkEmojiView( activity );
					GridView.LayoutParams lp = new GridView.LayoutParams( wh, wh );
					view.setLayoutParams( lp );
				}
				view.setTag( item );
				NetworkEmojiView iv = (NetworkEmojiView) view;
				if( page != null ){
					iv.setEmoji( emoji_url_map.get( item.name ) );
				}
			}
			
			return view;
		}
		
		@Override
		public void onItemClick( AdapterView< ? > adapterView, View view, int idx, long l ){
			if( page == null ) return;
			EmojiItem item = page.emoji_list.get( idx );
			if( TextUtils.isEmpty( item.instance ) ){
				String name = item.name;
				EmojiMap201709.EmojiInfo info = EmojiMap201709.sShortNameToImageId.get( name );
				if( info == null ) return;
				if( selected_tone != 0 ){
					name = applySkinTone( name );
				}
				selected( name, null );
			}else{
				selected( item.name, item.instance );
			}
		}
	}
	
	void selected( String name, String instance ){
		
		dialog.dismiss();
		
		// Recentをロード(他インスタンスの絵文字を含む)
		SharedPreferences pref = App1.pref;
		ArrayList< JSONObject > list = new ArrayList<>();
		String sv = pref.getString( Pref.KEY_EMOJI_PICKER_RECENT, null );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				JSONArray array = new JSONArray( sv );
				for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
					JSONObject item = array.optJSONObject( i );
					if( item != null ) list.add( item );
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		// 選択された絵文字と同じ項目を除去
		for( int i = list.size() - 1 ; i >= 0 ; -- i ){
			JSONObject item = list.get( i );
			if( name.equals( Utils.optStringX( item, "name" ) ) ){
				if( instance == null ? item.isNull( "instance" ) : instance.equals( Utils.optStringX( item, "instance" ) ) ){
					list.remove( i );
					
				}
			}
		}
		
		// 先頭に項目を追加
		try{
			JSONObject item = new JSONObject();
			item.put( "name", name );
			if( instance != null ) item.put( "instance", instance );
			list.add( 0, item );
		}catch( Throwable ignored ){
		}
		
		// 項目が増えすぎたら減らす
		while( list.size() >= 256 ){
			list.remove( list.size() - 1 );
		}
		
		// 保存する
		try{
			JSONArray array = new JSONArray();
			for( JSONObject item : list ){
				array.put( item );
			}
			App1.pref.edit().putString( Pref.KEY_EMOJI_PICKER_RECENT, array.toString() ).apply();
		}catch( Throwable ignored ){
			
		}
		
		callback.onPickedEmoji( name );
	}
	
	class EmojiPickerPagerAdapter extends PagerAdapter {
		
		private final LayoutInflater inflater;
		private final SparseArray< EmojiPickerPageViewHolder > holder_list = new SparseArray<>();
		
		EmojiPickerPagerAdapter(){
			this.inflater = activity.getLayoutInflater();
		}
		
		@Override public int getCount(){
			return page_list.size();
		}
		
		EmojiPickerPage getPage( int idx ){
			if( idx >= 0 && idx < page_list.size() ) return page_list.get( idx );
			return null;
		}
		
		EmojiPickerPageViewHolder getPageViewHolder( int idx ){
			return holder_list.get( idx );
		}
		
		@Override public CharSequence getPageTitle( int page_idx ){
			return getPage( page_idx ).title;
		}
		
		@Override public boolean isViewFromObject( View view, Object object ){
			return view == object;
		}
		
		@Override public Object instantiateItem( ViewGroup container, int page_idx ){
			View root = inflater.inflate( R.layout.page_emoji_picker, container, false );
			container.addView( root, 0 );
			
			EmojiPickerPage page = page_list.get( page_idx );
			EmojiPickerPageViewHolder holder = new EmojiPickerPageViewHolder( activity, root );
			//
			holder_list.put( page_idx, holder );
			//
			holder.onPageCreate( page );
			
			return root;
		}
		
		@Override public void destroyItem( ViewGroup container, int page_idx, Object object ){
			View view = (View) object;
			//
			container.removeView( view );
			//
			EmojiPickerPageViewHolder holder = holder_list.get( page_idx );
			holder_list.remove( page_idx );
			if( holder != null ){
				holder.onPageDestroy();
			}
		}
	}
	
}
