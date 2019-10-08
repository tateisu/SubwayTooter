package jp.juggler.subwaytooter

import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import android.widget.TextView
import jp.juggler.subwaytooter.action.Action_Account
import jp.juggler.subwaytooter.action.Action_Instance
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.LogCategory
import jp.juggler.util.neatSpaces
import jp.juggler.util.showToast
import org.conscrypt.OpenSSLX509Certificate

internal class ViewHolderHeaderInstance(
	arg_activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(arg_activity, viewRoot)
	, View.OnClickListener {
	
	companion object {
		private val log = LogCategory("ViewHolderHeaderInstance")
		
	}
	
	private val btnInstance : TextView = viewRoot.findViewById(R.id.btnInstance)
	private val tvVersion : TextView = viewRoot.findViewById(R.id.tvVersion)
	private val tvTitle : TextView = viewRoot.findViewById(R.id.tvTitle)
	private val btnEmail : TextView = viewRoot.findViewById(R.id.btnEmail)
	private val tvDescription : TextView = viewRoot.findViewById(R.id.tvDescription)
	private val tvShortDescription : TextView = viewRoot.findViewById(R.id.tvShortDescription)
	private val tvUserCount : TextView = viewRoot.findViewById(R.id.tvUserCount)
	private val tvTootCount : TextView = viewRoot.findViewById(R.id.tvTootCount)
	private val tvDomainCount : TextView = viewRoot.findViewById(R.id.tvDomainCount)
	private val ivThumbnail : MyNetworkImageView = viewRoot.findViewById(R.id.ivThumbnail)
	private val btnContact : TextView = viewRoot.findViewById(R.id.btnContact)
	private val tvLanguages : TextView = viewRoot.findViewById(R.id.tvLanguages)
	private val tvHandshake : TextView = viewRoot.findViewById(R.id.tvHandshake)
	
	private val btnAbout : Button = viewRoot.findViewById(R.id.btnAbout)
	private val btnAboutMore : Button = viewRoot.findViewById(R.id.btnAboutMore)
	private val btnExplore : Button = viewRoot.findViewById(R.id.btnExplore)
	
	private var instance : TootInstance? = null
	
	init {
		
		//
		//		CharSequence sv = HTMLDecoder.decodeHTML( activity, access_info, html, false, true, null );
		//
		//		TextView tvSearchDesc = (TextView) viewRoot.findViewById( R.id.tvSearchDesc );
		//		tvSearchDesc.setVisibility( View.VISIBLE );
		//		tvSearchDesc.setMovementMethod( MyLinkMovementMethod.getInstance() );
		//		tvSearchDesc.setText( sv );
		
		btnInstance.setOnClickListener(this)
		btnEmail.setOnClickListener(this)
		btnContact.setOnClickListener(this)
		ivThumbnail.setOnClickListener(this)
		
		btnAbout.setOnClickListener(this)
		btnAboutMore.setOnClickListener(this)
		btnExplore.setOnClickListener(this)
		
		tvDescription.movementMethod = MyLinkMovementMethod
		tvShortDescription.movementMethod = MyLinkMovementMethod
	}
	
	override fun showColor() {
		//
	}
	
	override fun bindData(column : Column) {
		super.bindData(column)
		val instance = column.instance_information
		val handshake = column.handshake
		this.instance = instance
		
		if(instance == null) {
			btnInstance.text = "?"
			tvVersion.text = "?"
			tvTitle.text = "?"
			btnEmail.text = "?"
			btnEmail.isEnabled = false
			tvDescription.text = "?"
			tvShortDescription.text = "?"
			ivThumbnail.setImageUrl(App1.pref, 0f, null)
			tvLanguages.text = "?"
			btnContact.text = "?"
			btnContact.isEnabled = false
			btnAbout.isEnabled = false
			btnAboutMore.isEnabled = false
			btnExplore.isEnabled = false
		} else {
			val uri = instance.uri ?: ""
			val hasUri = uri.isNotEmpty()
			btnInstance.text = uri
			
			btnInstance.isEnabled = hasUri
			btnAbout.isEnabled = hasUri
			btnAboutMore.isEnabled = hasUri
			btnExplore.isEnabled = hasUri
			
			tvVersion.text = instance.version ?: ""
			tvTitle.text = instance.title ?: ""
			
			val email = instance.email ?: ""
			btnEmail.text = email
			btnEmail.isEnabled = email.isNotEmpty()
			
			val contact_acct =
				instance.contact_account?.let { who -> "@" + who.username + "@" + who.host } ?: ""
			btnContact.text = contact_acct
			btnContact.isEnabled = contact_acct.isNotEmpty()
			
			tvLanguages.text = instance.languages?.joinToString(", ") ?: ""
			
			val options = DecodeOptions(activity, access_info, decodeEmoji = true)
			
			tvShortDescription.text = options
				.decodeHTML("<p>${instance.short_description ?: ""}</p>")
				.neatSpaces()
			
			tvDescription.text = options
				.decodeHTML("<p>${instance.description ?: ""}</p>")
				.neatSpaces()
			
			val stats = instance.stats
			if(stats == null) {
				tvUserCount.setText(R.string.not_provided_mastodon_under_1_6)
				tvTootCount.setText(R.string.not_provided_mastodon_under_1_6)
				tvDomainCount.setText(R.string.not_provided_mastodon_under_1_6)
			} else {
				tvUserCount.text = stats.user_count.toString(10)
				tvTootCount.text = stats.status_count.toString(10)
				tvDomainCount.text = stats.domain_count.toString(10)
				
			}
			
			val thumbnail = instance.thumbnail
			if(thumbnail == null || thumbnail.isEmpty()) {
				ivThumbnail.setImageUrl(App1.pref, 0f, null)
			} else {
				ivThumbnail.setImageUrl(App1.pref, 0f, thumbnail, thumbnail)
			}
			
		}
		
		tvHandshake.text = if(handshake == null) {
			""
		} else {
			val sb = SpannableStringBuilder("${handshake.tlsVersion}, ${handshake.cipherSuite}")
			val certs = handshake.peerCertificates.joinToString("\n") { cert ->
				"\n============================\n" +
					if(cert is OpenSSLX509Certificate) {
						
						log.d(cert.toString())
						
						"""
						Certificate : ${cert.type}
						subject : ${cert.subjectDN}
						subjectAlternativeNames : ${
						cert.subjectAlternativeNames
							?.joinToString(", ") {
								try {
									it?.last()
								} catch(ignored : Throwable) {
									it
								}
									?.toString() ?: "null"
							}
						}
						issuer : ${cert.issuerX500Principal}
						end : ${cert.notAfter}
						""".trimIndent()
						
					} else {
						cert.javaClass.name + "\n" + cert.toString()
					}
			}
			if(certs.isNotEmpty()) {
				sb.append('\n')
				sb.append(certs)
			}
			sb
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnEmail -> instance?.email?.let { email ->
				try {
					if(email.contains("://")) {
						App1.openCustomTab(activity, email)
					} else {
						val intent = Intent(Intent.ACTION_SEND)
						intent.type = "text/plain"
						intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
						intent.putExtra(Intent.EXTRA_TEXT, email)
						activity.startActivity(intent)
					}
					
				} catch(ex : Throwable) {
					log.e(ex, "startActivity failed. mail=$email")
					showToast(activity, true, R.string.missing_mail_app)
				}
				
			}
			
			R.id.btnContact -> instance?.contact_account?.let { who ->
				Action_Account.timeline(
					activity
					, activity.nextPosition(column)
					, ColumnType.SEARCH
					
					, args = arrayOf("@" + who.username + "@" + who.host, true)
				)
			}
			
			R.id.btnInstance -> App1.openBrowser(activity, "https://${column.instance_uri}/about")
			R.id.ivThumbnail -> App1.openBrowser(activity, instance?.thumbnail)
			
			R.id.btnAbout ->
				App1.openBrowser(activity, "https://${column.instance_uri}/about")
			
			R.id.btnAboutMore ->
				App1.openBrowser(activity, "https://${column.instance_uri}/about/more")
			
			R.id.btnExplore -> Action_Instance.profileDirectoryFromInstanceInformation(
				activity,
				column,
				column.instance_uri,
				instance = instance
			)
		}
	}
	
	override fun onViewRecycled() {
	}
	
}
