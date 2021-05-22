package jp.juggler.util

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UriOrNullSerializer : KSerializer<Uri?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UriOrNull", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri?) =
        encoder.encodeString(value?.toString() ?:"" )

    override fun deserialize(decoder: Decoder): Uri? =
        decoder.decodeString().mayUri()
}
