package com.yunx.app.data.preview

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MacFfmpegInstallerTest {
    @Test fun choosesNativeMacArchitecture() {
        assertEquals("ffmpeg-darwin-arm64", MacFfmpegInstaller.assetName("aarch64"))
        assertEquals("ffmpeg-darwin-arm64", MacFfmpegInstaller.assetName("arm64"))
        assertEquals("ffmpeg-darwin-x64", MacFfmpegInstaller.assetName("x86_64"))
        assertEquals("ffmpeg-darwin-x64", MacFfmpegInstaller.assetName("amd64"))
    }
    @Test(expected = IOException::class) fun rejectsUnknownArchitecture() {
        MacFfmpegInstaller.assetName("riscv64")
    }
}
