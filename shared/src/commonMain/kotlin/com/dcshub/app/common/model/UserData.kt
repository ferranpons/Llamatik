package com.dcshub.app.common.model

import com.dcshub.app.common.model.theme.DarkThemeConfig
import com.dcshub.app.common.model.theme.ThemeBrand

data class UserData(
    val bookmarkedNewsResources: Set<String>,
    val themeBrand: ThemeBrand,
    val darkThemeConfig: DarkThemeConfig
)
