package com.mebonsoft.memorix.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelNavigationSupportTest {
    @Test
    fun isRouteInTopLevelSection_treatsComposeAsWorkSection() {
        assertTrue(isRouteInTopLevelSection("work/compose", "work"))
    }

    @Test
    fun isRouteInTopLevelSection_treatsMediaDetailAsNonTopLevelSection() {
        assertFalse(isRouteInTopLevelSection("media/12", "work"))
        assertFalse(isRouteInTopLevelSection("settings/hidden-vault", "work"))
    }

    @Test
    fun isRouteInTopLevelSection_matchesExactTopLevelRoute() {
        assertTrue(isRouteInTopLevelSection("home", "home"))
    }
}
