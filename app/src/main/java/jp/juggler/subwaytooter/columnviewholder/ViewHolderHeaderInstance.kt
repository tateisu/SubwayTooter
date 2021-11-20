package jp.juggler.subwaytooter.columnviewholder

import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import android.widget.TextView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.serverProfileDirectoryFromInstanceInformation
import jp.juggler.subwaytooter.action.timeline
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import org.conscrypt.OpenSSLX509Certificate

internal class ViewHolderHeaderInstance(
    activityArg: ActMain,
    viewRoot: View,
) : ViewHolderHeaderBase(activityArg, viewRoot), View.OnClickListener {

    companion object {
        private val log = LogCategory("ViewHolderHeaderInstance")
    }

    private val btnInstance: TextView = viewRoot.findViewById(R.id.btnInstance)
    private val tvVersion: TextView = viewRoot.findViewById(R.id.tvVersion)
    private val tvTitle: TextView = viewRoot.findViewById(R.id.tvTitle)
    private val btnEmail: TextView = viewRoot.findViewById(R.id.btnEmail)
    private val tvDescription: TextView = viewRoot.findViewById(R.id.tvDescription)
    private val tvShortDescription: TextView = viewRoot.findViewById(R.id.tvShortDescription)
    private val tvUserCount: TextView = viewRoot.findViewById(R.id.tvUserCount)
    private val tvTootCount: TextView = viewRoot.findViewById(R.id.tvTootCount)
    private val tvDomainCount: TextView = viewRoot.findViewById(R.id.tvDomainCount)
    private val ivThumbnail: MyNetworkImageView = viewRoot.findViewById(R.id.ivThumbnail)
    private val btnContact: TextView = viewRoot.findViewById(R.id.btnContact)
    private val tvLanguages: TextView = viewRoot.findViewById(R.id.tvLanguages)
    private val tvInvitesEnabled: TextView = viewRoot.findViewById(R.id.tvInvitesEnabled)
    private val tvHandshake: TextView = viewRoot.findViewById(R.id.tvHandshake)

    private val btnAbout: Button = viewRoot.findViewById(R.id.btnAbout)
    private val btnAboutMore: Button = viewRoot.findViewById(R.id.btnAboutMore)
    private val btnExplore: Button = viewRoot.findViewById(R.id.btnExplore)

    private var instance: TootInstance? = null

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

    override fun bindData(column: Column) {
        super.bindData(column)
        val instance = column.instanceInformation
        val handshake = column.handshake
        this.instance = instance

        if (instance == null) {
            btnInstance.text = "?"
            tvVersion.text = "?"
            tvTitle.text = "?"
            btnEmail.text = "?"
            btnEmail.isEnabledAlpha = false
            tvDescription.text = "?"
            tvShortDescription.text = "?"
            ivThumbnail.setImageUrl(0f, null)
            tvLanguages.text = "?"
            tvInvitesEnabled.text = "?"
            btnContact.text = "?"
            btnContact.isEnabledAlpha = false
            btnAbout.isEnabledAlpha = false
            btnAboutMore.isEnabledAlpha = false
            btnExplore.isEnabledAlpha = false
        } else {
            val uri = instance.uri ?: ""
            val hasUri = uri.isNotEmpty()

            val host = Host.parse(uri)
            btnInstance.text = if (host.ascii == host.pretty) {
                host.pretty
            } else {
                "${host.pretty}\n${host.ascii}"
            }

            btnInstance.isEnabledAlpha = hasUri
            btnAbout.isEnabledAlpha = hasUri
            btnAboutMore.isEnabledAlpha = hasUri
            btnExplore.isEnabledAlpha = hasUri

            tvVersion.text = instance.version ?: ""
            tvTitle.text = instance.title ?: ""

            val email = instance.email ?: ""
            btnEmail.text = email
            btnEmail.isEnabledAlpha = email.isNotEmpty()

            val contactAcct =
                instance.contact_account?.let { who -> "@${who.username}@${who.apDomain.pretty}" }
                    ?: ""
            btnContact.text = contactAcct
            btnContact.isEnabledAlpha = contactAcct.isNotEmpty()

            tvLanguages.text = instance.languages?.joinToString(", ") ?: ""
            tvInvitesEnabled.text = when (instance.invites_enabled) {
                null -> "?"
                true -> activity.getString(R.string.yes)
                false -> activity.getString(R.string.no)
            }

            val options = DecodeOptions(
                activity,
                accessInfo,
                decodeEmoji = true,
                mentionDefaultHostDomain = accessInfo
            )

            tvShortDescription.text = options
                .decodeHTML("<p>${instance.short_description ?: ""}</p>")
                .neatSpaces()

            tvDescription.text = options
                .decodeHTML("<p>${instance.description ?: ""}</p>")
                .neatSpaces()

            val stats = instance.stats
            if (stats == null) {
                tvUserCount.setText(R.string.not_provided_mastodon_under_1_6)
                tvTootCount.setText(R.string.not_provided_mastodon_under_1_6)
                tvDomainCount.setText(R.string.not_provided_mastodon_under_1_6)
            } else {
                tvUserCount.text = stats.user_count.toString(10)
                tvTootCount.text = stats.status_count.toString(10)
                tvDomainCount.text = stats.domain_count.toString(10)
            }

            val thumbnail = instance.thumbnail?.let {
                if (it.startsWith("/")) {
                    // "/instance/thumbnail.jpeg" in case of pleroma.noellabo.jp
                    "https://${host.ascii}$it"
                } else {
                    it
                }
            }.notEmpty()
            ivThumbnail.setImageUrl(0f, thumbnail, thumbnail)
        }

        tvHandshake.text = if (handshake == null) {
            ""
        } else {
            val sb = SpannableStringBuilder("${handshake.tlsVersion}, ${handshake.cipherSuite}")
            val certs = handshake.peerCertificates.joinToString("\n") { cert ->
                "\n============================\n" +
                        if (cert is OpenSSLX509Certificate) {

                            log.d(cert.toString())

                            """
						Certificate : ${cert.type}
						subject : ${cert.subjectDN}
						subjectAlternativeNames : ${
                                cert.subjectAlternativeNames
                                    ?.joinToString(", ") {
                                        try {
                                            it?.last()
                                        } catch (ignored: Throwable) {
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
            if (certs.isNotEmpty()) {
                sb.append('\n')
                sb.append(certs)
            }
            sb
        }
    }

    override fun onClick(v: View) {
        val host = Host.parse(column.instanceUri)
        when (v.id) {

            R.id.btnEmail -> instance?.email?.let { email ->
                try {
                    if (email.contains("://")) {
                        activity.openCustomTab(email)
                    } else {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        intent.putExtra(Intent.EXTRA_TEXT, email)
                        activity.startActivity(intent)
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "startActivity failed. mail=$email")
                    activity.showToast(true, R.string.missing_mail_app)
                }
            }

            R.id.btnContact -> instance?.contact_account?.let { who ->
                activity.timeline(
                    activity.nextPosition(column),
                    ColumnType.SEARCH,
                    args = arrayOf("@${who.username}@${who.apDomain.ascii}", true)
                )
            }

            R.id.btnInstance ->
                activity.openBrowser("https://${host.ascii}/about")

            R.id.ivThumbnail ->
                activity.openBrowser(instance?.thumbnail)

            R.id.btnAbout ->
                activity.openBrowser("https://${host.ascii}/about")

            R.id.btnAboutMore ->
                activity.openBrowser("https://${host.ascii}/about/more")

            R.id.btnExplore -> activity.serverProfileDirectoryFromInstanceInformation(
                column,
                host,
                instance = instance
            )
        }
    }

    override fun onViewRecycled() {
    }
}
