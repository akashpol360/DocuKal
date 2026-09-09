package com.docukal.app.navigation

object Routes {
    const val HOME = "home"
    const val DOCUMENTS = "documents"
    const val WARRANTIES = "warranties"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val ADD_DOCUMENT = "add_document"
    const val DOCUMENT_DETAIL = "document/{id}"

    fun documentDetail(id: Long) = "document/$id"
}
