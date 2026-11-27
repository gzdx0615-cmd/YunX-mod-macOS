package com.yunx.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class MacSystemProxyTest {
    @Test fun prefersEnabledHttpsProxyAndIgnoresDisabledEntries() {
        assertEquals(SystemProxy.Inspect.Proxy("127.0.0.1", 7890), SystemProxy.parseMac("""
            <dictionary> {
              HTTPEnable : 0
              HTTPProxy : stale.local
              HTTPPort : 1234
              HTTPSEnable : 1
              HTTPSProxy : 127.0.0.1
              HTTPSPort : 7890
            }
        """.trimIndent()))
    }
    @Test fun reportsPacInsteadOfSilentlyUsingFixedProxy() {
        assertEquals(SystemProxy.Inspect.PacUnsupported, SystemProxy.parseMac("""
            ProxyAutoConfigEnable : 1
            HTTPEnable : 1
            HTTPProxy : 127.0.0.1
            HTTPPort : 7890
        """.trimIndent()))
    }
    @Test fun rejectsInvalidPorts() {
        assertEquals(SystemProxy.Inspect.Direct, SystemProxy.parseMac("""
            HTTPEnable : 1
            HTTPProxy : 127.0.0.1
            HTTPPort : 99999
        """.trimIndent()))
    }
}
