package com.yunx.app.data.update

import org.junit.Assert.*
import org.junit.Test

class MacReleaseAssetTest {
    @Test fun selectsOnlyMatchingCpuOrUniversalAsset() {
        assertTrue(UpdateChecker.compatibleMacAsset("YunX-mod-macOS-arm64.dmg", "aarch64"))
        assertFalse(UpdateChecker.compatibleMacAsset("YunX-mod-macOS-x86_64.dmg", "aarch64"))
        assertTrue(UpdateChecker.compatibleMacAsset("YunX-mod-macOS-x86_64.dmg", "x86_64"))
        assertFalse(UpdateChecker.compatibleMacAsset("YunX-mod-macOS-arm64.dmg", "x86_64"))
        assertTrue(UpdateChecker.compatibleMacAsset("YunX-OS-universal.dmg", "aarch64"))
    }
}
