package com.boxy.authenticator.test.core

import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.utils.cleanSecretKey
import com.boxy.authenticator.utils.getInitials
import com.boxy.authenticator.utils.name
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun `initials use at most two trimmed words`() {
        assertEquals("JD", "  jane   doe smith ".getInitials())
        assertEquals("E", "example".getInitials())
        assertEquals("?", "".getInitials())
    }

    @Test
    fun `secret cleaner removes whitespace and padding then uppercases`() {
        assertEquals("JBSWY3DPEHPK3PXP", " jb swy3dp\nehpk3pxp\t====".cleanSecretKey())
    }

    @Test
    fun `token name includes label only when present`() {
        assertEquals("Example (account)", testToken(label = "account").name)
        assertEquals("Example", testToken(label = "").name)
    }
}
