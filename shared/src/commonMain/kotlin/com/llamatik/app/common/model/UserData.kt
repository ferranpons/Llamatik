package com.llamatik.app.common.model

import com.llamatik.app.common.model.theme.DarkThemeConfig
import com.llamatik.app.common.model.theme.ThemeBrand

data class UserData(
    val bookmarkedNewsResources: Set<String>,
    val themeBrand: ThemeBrand,
    val darkThemeConfig: DarkThemeConfig
)
