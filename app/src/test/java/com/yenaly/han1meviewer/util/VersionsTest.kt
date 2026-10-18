package com.yenaly.han1meviewer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun fourPartReleaseTagsCompareWithoutRequiringBuildCodeSuffix() {
        assertFalse(checkNeedUpdate("v1.1.0.1"))
        assertFalse(checkNeedUpdate("1.1.0.1-release"))
        assertFalse(checkNeedUpdate("v1.1.0"))
        assertTrue(checkNeedUpdate("v1.1.0.2"))
        assertTrue(checkNeedUpdate("v1.1.1"))
        assertFalse(checkNeedUpdate("not-a-version"))
    }
}
