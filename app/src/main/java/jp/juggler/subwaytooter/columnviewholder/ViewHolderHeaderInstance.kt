package jp.juggler.subwaytooter.columnviewholder

import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.serverProfileDirectoryFromInstanceInformation
import jp.juggler.subwaytooter.action.timeline
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.databinding.LvHeaderInstanceBinding
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.util.data.neatSpaces
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.isEnabledAlpha
import org.conscrypt.OpenSSLX509Certificate

internal class ViewHolderHeaderInstance(
    override val activity: ActMain,
    parent: ViewGroup,
    private val views: LvHeaderInstanceBinding =
        LvHeaderInstanceBinding.inflate(activity.layoutInflater, parent, false),
) : ViewHolderHeaderBase(views.root), View.OnClickListener {

    companion object {
        private val log = LogCategory("ViewHolderHeaderInstance")
    }

    private var instance: TootInstance? = null

    init {
        views.root.tag = this

        //		CharSequence sv = HTMLDecoder.decodeHTML( activity, access_info, html, false, true, null );
        //		TextView tvSearchDesc = (TextView) viewRoot.findViewById( R.id.tvSearchDesc );
        //		tvSearchDesc.setVisibility( View.VISIBLE );
        //		tvSearchDesc.setMovementMethod( MyLinkMovementMethod.getInstance() );
        //		tvSearchDesc.setText( sv );

        arrayOf(
            views.btnInstance,
            views.btnEmail,
            views.btnContact,
            views.ivThumbnail,
            views.btnAbout,
            views.btnAboutMore,
            views.btnExplore,
        ).forEach {
            it.setOnClickListener(this)
        }

        views.tvDescription.movementMethod = MyLinkMovementMethod
        views.tvShortDescription.movementMethod = MyLinkMovementMethod
    }

    override fun showColor() {
        //
    }

    override fun bindData(column: Column) {
        super.bindData(column)
        views.run {
            val instance = column.instanceInformation
            this@ViewHolderHeaderInstance.instance = instance

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
                tvConfiguration.text = ""
                tvFedibirdCapacities.text = ""
            } else {
                val domain = instance.apDomain
                btnInstance.text = when {
                    domain.pretty != domain.ascii -> "${domain.pretty}\n${domain.ascii}"
                    else -> domain.ascii
                }

                btnInstance.isEnabledAlpha = true
                btnAbout.isEnabledAlpha = true
                btnAboutMore.isEnabledAlpha = true
                btnExplore.isEnabledAlpha = true

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
                    authorDomain = accessInfo
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
                        "https://${instance.apiHost.ascii}$it"
                    } else {
                        it
                    }
                }.notEmpty()
                ivThumbnail.setImageUrl(0f, thumbnail, thumbnail)

                tvConfiguration.text =
                    instance.configuration?.toString(1, sort = true) ?: ""
                tvFedibirdCapacities.text =
                    instance.fedibirdCapabilities?.sorted()?.joinToString("\n") ?: ""
            }

            tvHandshake.text = when (val handshake = column.handshake) {
                null -> ""
                else -> {
                    val sb = SpannableStringBuilder(
                        "${handshake.tlsVersion}, ${handshake.cipherSuite}"
                    )
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
