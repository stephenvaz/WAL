package com.stephen.nativewal.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
enum class FormActionType {
    setValue,
    click;

    companion object {
        fun fromString(value: String): FormActionType {
            return entries.firstOrNull { it.name == value } ?: setValue
        }
    }
}

@Serializable
data class FormAction(
    val type: FormActionType = FormActionType.setValue,
    val selector: String = "",
    val value: String? = null,
    val order: Int = 0,
    @Transient val id: String = UUID.randomUUID().toString()
)
