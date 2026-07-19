package com.mebonsoft.memorix.app.navigation

internal fun isRouteInTopLevelSection(
    route: String?,
    topLevelRoute: String,
): Boolean = route == topLevelRoute || route?.startsWith("$topLevelRoute/") == true
