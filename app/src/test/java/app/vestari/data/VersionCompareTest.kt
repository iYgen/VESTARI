package app.vestari.data

import app.vestari.update.Updater
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newerMinor() = assertTrue(Updater.compareVersions("0.2.0", "0.1.9") > 0)
    @Test fun numericNotLexical() = assertTrue(Updater.compareVersions("1.10.0", "1.9.3") > 0)
    @Test fun equal() = assertTrue(Updater.compareVersions("1.0", "1.0.0") == 0)
    @Test fun older() = assertTrue(Updater.compareVersions("0.1.0", "0.1.1") < 0)
    @Test fun suffixIgnored() = assertTrue(Updater.compareVersions("1.2.0-beta", "1.2.0") == 0)
}
