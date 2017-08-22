package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import jp.juggler.subwaytooter.ActPost;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.PostDraft;
import jp.juggler.subwaytooter.util.Utils;

public class DlgDraftPicker
	implements AdapterView.OnItemClickListener
	, AdapterView.OnItemLongClickListener
	, DialogInterface.OnDismissListener
{
	

	public interface Callback{
		void onDraftSelected(JSONObject draft);
	}
	
	private ActPost activity;
	private Callback callback;
	private ListView lvDraft;
	private MyAdapter adapter;
	private AlertDialog dialog;
	private Cursor cursor;
	private PostDraft.ColIdx colIdx;
	
	private AsyncTask<Void,Void,Cursor> task;
	
	public DlgDraftPicker(){
	}
	
	@Override public void onItemClick( AdapterView< ? > parent, View view, int position, long id ){
		PostDraft draft = getPostDraft( position );
		if( draft != null ){
			callback.onDraftSelected( draft.json );
			dialog.dismiss();
		}
	}

	@Override public boolean onItemLongClick( AdapterView< ? > parent, View view, int position, long id ){

		PostDraft draft = getPostDraft( position );
		if( draft != null ){
			Utils.showToast( activity,false,R.string.draft_deleted );
			draft.delete();
			reload();
			return true;
		}

		return false;
	}
	
	@Override public void onDismiss( DialogInterface dialog ){
		if( task != null){
			task.cancel( true );
			task = null;
		}

		lvDraft.setAdapter( null );

		if( cursor != null ){
			cursor.close();
		}
	}
	
	public void open(final ActPost _activity,final Callback _callback){
		this.activity = _activity;
		this.callback = _callback;
		
		adapter = new MyAdapter();
		
		@SuppressLint("InflateParams")
		View viewRoot = activity.getLayoutInflater().inflate( R.layout.dlg_draft_picker, null, false );
		this.lvDraft = (ListView) viewRoot.findViewById( R.id.lvDraft );
		
		lvDraft.setOnItemClickListener( this );
		lvDraft.setOnItemLongClickListener( this );
		lvDraft.setAdapter( adapter );
		
		this.dialog = new AlertDialog.Builder( activity )
			.setTitle( R.string.select_draft )
			.setNegativeButton( R.string.cancel, null )
			.setView( viewRoot )
			.create()
		;
		dialog.setOnDismissListener( this );
		
		
		dialog.show();
		
		reload();
	}
	
	private void reload(){
		
		if( task != null){
			task.cancel( true );
			task = null;
		}
		
		task = new AsyncTask< Void, Void, Cursor >() {
			@Override protected Cursor doInBackground( Void... params ){
				return PostDraft.createCursor();
			}
			
			@Override protected void onCancelled( Cursor cursor ){
				super.onCancelled( cursor );
			}
			@Override protected void onPostExecute( Cursor cursor ){
				if( ! dialog.isShowing() ){
					// dialog is already closed.
					if( cursor != null ) cursor.close();
					return;
				}
				
				if( cursor == null ){
					// load failed.
					Utils.showToast( activity, true, "failed to loading drafts." );
				}else{
					DlgDraftPicker.this.cursor = cursor;
					colIdx = new PostDraft.ColIdx(cursor);
					adapter.notifyDataSetChanged();
				}
			}
		};
		task.executeOnExecutor( App1.task_executor );
	}
	
	private PostDraft getPostDraft( int position ){
		if( cursor == null ) return null;
		return PostDraft.loadFromCursor( cursor,colIdx, position);
	}
	
	private class MyViewHolder {
		TextView tvTime;
		TextView tvText;
		
		MyViewHolder( View view ){
			tvTime = (TextView) view.findViewById( R.id.tvTime );
			tvText = (TextView) view.findViewById( R.id.tvText );
		}
		
		public void bind( int position ){
			PostDraft draft = getPostDraft( position );
			if( draft == null ) return;
			
			tvTime.setText( TootStatus.formatTime( tvTime.getContext(),draft.time_save ,false ));
			
			String cw = draft.json.optString( ActPost.DRAFT_CONTENT_WARNING );
			String c = draft.json.optString( ActPost.DRAFT_CONTENT );
			StringBuilder sb = new StringBuilder();
			if( ! TextUtils.isEmpty( cw.trim() ) ){
				sb.append( cw );
			}
			if( ! TextUtils.isEmpty( c.trim() ) ){
				if( sb.length() > 0 ) sb.append( "\n" );
				sb.append( c );
			}
			tvText.setText( sb );
		}
	}
	
	private class MyAdapter extends BaseAdapter{
		
		@Override public int getCount(){
			if( cursor == null ) return 0;
			return cursor.getCount();
		}
		
		@Override public Object getItem( int position ){
			return getPostDraft(position);
		}
		
		@Override public long getItemId( int position ){
			return 0;
		}
		
		@Override public View getView( int position, View view, ViewGroup parent ){
			MyViewHolder holder;
			if( view == null ){
				view = activity.getLayoutInflater().inflate( R.layout.lv_draft_picker, parent, false );
				holder = new MyViewHolder( view );
				view.setTag(holder);
			}else{
				holder = (MyViewHolder) view.getTag();
			}
			holder.bind( position );
			return view;
		}
	}
}
