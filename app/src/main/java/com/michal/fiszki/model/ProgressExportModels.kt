package com.michal.fiszki.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ProgressExport(
    val languageName: String,
    val themeName: String,
    val partIndex: Int,
    val status: ThemeStatus,
    @Serializable(with = LocalDateSerializer::class)
    val lastCompletedDate: LocalDate?,
    @Serializable(with = LocalDateSerializer::class)
    val nextAvailableDate: LocalDate?,
    val isInProgress: Boolean
)

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
