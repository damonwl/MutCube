package com.dwl.mutcube.storage

import org.junit.Assert.*
import org.junit.Test

class UserProfileTest {
    @Test fun nicknameDefaultsAndTrimsWithoutChangingOtherSettings() {
        assertEquals("用户", DisplaySettings().userDisplayName)
        assertEquals("用户", DisplaySettings(userNickname = " \n\t ").userDisplayName)
        val before = DisplaySettings(themeMode = ThemeMode.DARK, autoScroll = false)
        val after = before.copy(userNickname = "  测试昵称  ", userAvatarFile = "avatar")
        assertEquals("测试昵称", after.userDisplayName)
        assertEquals(ThemeMode.DARK, after.themeMode)
        assertFalse(after.autoScroll)
        assertEquals("", DisplaySettings().userAvatarFile)
    }

    @Test fun avatarOnlyAcceptsOwnedImageNames() {
        assertTrue(isUserAvatarFileName("01234567-89ab-cdef-0123-456789abcdef.jpg"))
        assertFalse(isUserAvatarFileName(""))
        assertFalse(isUserAvatarFileName("../../document.jpg"))
        assertFalse(isUserAvatarFileName("/sdcard/picture.jpg"))
        assertFalse(isUserAvatarFileName("01234567-89ab-cdef-0123-456789abcdef.png"))
    }
}
