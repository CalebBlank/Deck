package com.hermes.deck.data

data class CardGroup(
    val id: String,
    val apps: List<AppInfo>
) {
    val isStack: Boolean get() = apps.size > 1
    val primaryApp: AppInfo get() = apps.first()

    companion object {
        fun single(app: AppInfo) = CardGroup(id = app.id, apps = listOf(app))

        fun stack(apps: List<AppInfo>): CardGroup {
            require(apps.isNotEmpty())
            return CardGroup(
                id   = apps.joinToString("+") { it.id },
                apps = apps
            )
        }
    }
}
