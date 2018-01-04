package jp.juggler.subwaytooter

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView

import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyListView
import jp.juggler.subwaytooter.view.MyNetworkImageView

internal class HeaderViewHolderInstance(
	arg_activity : ActMain,
	arg_column : Column,
	parent : MyListView
) : HeaderViewHolderBase(
	arg_activity,
	arg_column,
	arg_activity.layoutInflater.inflate(R.layout.lv_header_instance, parent, false)
) , View.OnClickListener {
	
	companion object {
		private val log = LogCategory("HeaderViewHolderInstance")
	}

	private val btnInstance : TextView
	private val tvVersion : TextView
	private val tvTitle : TextView
	private val btnEmail : TextView
	private val tvDescription : TextView
	private val tvUserCount : TextView
	private val tvTootCount : TextView
	private val tvDomainCount : TextView
	private val ivThumbnail : MyNetworkImageView
	
	private var instance : TootInstance? = null
	
	init {
		
		if(activity.timeline_font != null) {
			Utils.scanView(viewRoot) { v ->
				try {
					if(v is Button) {
						// ボタンは太字なので触らない
					} else if(v is TextView) {
						v.typeface = activity.timeline_font
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		//
		//		CharSequence sv = HTMLDecoder.decodeHTML( activity, access_info, html, false, true, null );
		//
		//		TextView tvSearchDesc = (TextView) viewRoot.findViewById( R.id.tvSearchDesc );
		//		tvSearchDesc.setVisibility( View.VISIBLE );
		//		tvSearchDesc.setMovementMethod( MyLinkMovementMethod.getInstance() );
		//		tvSearchDesc.setText( sv );
		
		btnInstance = viewRoot.findViewById(R.id.btnInstance)
		tvVersion = viewRoot.findViewById(R.id.tvVersion)
		tvTitle = viewRoot.findViewById(R.id.tvTitle)
		btnEmail = viewRoot.findViewById(R.id.btnEmail)
		tvDescription = viewRoot.findViewById(R.id.tvDescription)
		tvUserCount = viewRoot.findViewById(R.id.tvUserCount)
		tvTootCount = viewRoot.findViewById(R.id.tvTootCount)
		tvDomainCount = viewRoot.findViewById(R.id.tvDomainCount)
		ivThumbnail = viewRoot.findViewById(R.id.ivThumbnail)
		
		btnInstance.setOnClickListener(this)
		btnEmail.setOnClickListener(this)
		ivThumbnail.setOnClickListener(this)
		
		tvDescription.movementMethod = MyLinkMovementMethod.instance
	}
	
	override fun showColor() {
		//
	}
	
	override fun bindData(column : Column) {
		val instance = column.instance_information
		this.instance = instance
		
		if(instance == null) {
			btnInstance.text = "?"
			tvVersion.text = "?"
			tvTitle.text = "?"
			btnEmail.text = "?"
			btnEmail.isEnabled = false
			tvDescription.text = "?"
			ivThumbnail.setImageUrl(App1.pref, 0f, null)
		} else {
			val uri = instance.uri ?: ""
			btnInstance.text = uri
			btnInstance.isEnabled = uri.isNotEmpty()

			tvVersion.text = instance .version ?: ""
			tvTitle.text = instance .title ?: ""

			val email = instance .email ?:""
			btnEmail.text = email
			btnEmail.isEnabled = email.isNotEmpty()
			
			val sb = DecodeOptions()
				.setDecodeEmoji(true)
				.decodeHTML(activity, access_info, "<p>" + (instance .description ?: "") + "</p>")
			
			var previous_br_count = 0
			var i = 0
			while(i < sb.length) {
				val c = sb[i]
				if(c != '\n') {
					previous_br_count = 0
				} else {
					++ previous_br_count
					if(previous_br_count >= 3) {
						sb.delete(i, i + 1)
						-- previous_br_count
						-- i
					}
				}
				++ i
			}
			tvDescription.text = sb
			
			val stats = instance.stats
			if( stats == null) {
				tvUserCount.setText(R.string.not_provided_mastodon_under_1_6)
				tvTootCount.setText(R.string.not_provided_mastodon_under_1_6)
				tvDomainCount.setText(R.string.not_provided_mastodon_under_1_6)
			} else {
				tvUserCount.text = stats.user_count.toString(10)
				tvTootCount.text = stats.status_count.toString(10)
				tvDomainCount.text = stats.domain_count.toString(10)
				
			}
			
			val thumbnail = instance.thumbnail
			if(thumbnail == null || thumbnail.isEmpty() ) {
				ivThumbnail.setImageUrl(App1.pref, 0f, null)
			} else {
				ivThumbnail.setImageUrl(App1.pref, 0f,thumbnail, thumbnail)
			}
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnInstance -> instance?.uri?.let{ uri ->
				App1.openCustomTab(activity, "https://$uri/about")
			}
			
			R.id.btnEmail -> instance?.email?.let{ email->
				try {
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = "text/plain"
					intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
					intent.putExtra(Intent.EXTRA_TEXT, email)
					activity.startActivity(intent)
					
				} catch(ex : Throwable) {
					ex.printStackTrace()
					Utils.showToast(activity, true, R.string.missing_mail_app)
				}
				
			}
			
			R.id.ivThumbnail -> instance?.thumbnail?.let{ thumbnail ->
				try {
					if( thumbnail.isNotEmpty() ){
						val intent = Intent(Intent.ACTION_VIEW)
						intent.data = Uri.parse(thumbnail)
						activity.startActivity(intent)
					}
					
				} catch(ex : Throwable) {
					ex.printStackTrace()
					Utils.showToast(activity, true, "missing web browser")
				}
				
			}
		}
	}
	
	//	private void setContent( @NonNull WebView wv, @NonNull String html, @NonNull String mime_type ){
	//		html = "<html><meta charset=\"UTF-8\"><p>"+html+"</p></html>";
	//		wv.clearHistory();
	//		wv.loadData( Base64.encodeToString( Utils.encodeUTF8(html) ,Base64.NO_WRAP), mime_type+";charset=UTF-8", "base64");
	//	}
	
}
