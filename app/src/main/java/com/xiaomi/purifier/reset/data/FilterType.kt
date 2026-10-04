package com.xiaomi.purifier.reset.data

enum class FilterType(
    val productIdHex: String,
    val displayNameVi: String,
    val displayNameEn: String,
    val colorBadge: String
) {
    REGULAR(
        "00003033",
        "Lõi Chuẩn / HEPA (Xanh dương / Đen)",
        "Standard HEPA Filter (Gray / Blue)",
        "#3B82F6"
    ),
    FORMALDEHYDE(
        "00003031",
        "Lõi Khử Formaldehyde (Xanh lá)",
        "Anti-Formaldehyde Filter (Green)",
        "#10B981"
    ),
    ANTI_BACTERIAL(
        "00004130",
        "Lõi Kháng Khuẩn (Tím)",
        "Anti-Bacterial Filter (Purple)",
        "#8B5CF6"
    ),
    UNKNOWN(
        "",
        "Lõi Khác / Chưa rõ mã",
        "Custom / Unknown Filter",
        "#6B7280"
    );

    val displayName: String
        get() = displayNameEn

    fun getDisplayName(context: android.content.Context): String {
        val lang = context.resources.configuration.locales[0].language
        return if (lang == "vi") displayNameVi else displayNameEn
    }

    companion object {
        fun fromProductId(productIdHex: String): FilterType {
            val clean = productIdHex.replace(" ", "").uppercase()
            return entries.find { it != UNKNOWN && clean.contains(it.productIdHex) } ?: UNKNOWN
        }
    }
}
