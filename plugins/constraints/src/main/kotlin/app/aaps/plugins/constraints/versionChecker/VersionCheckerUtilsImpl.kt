package app.aaps.plugins.constraints.versionChecker

import android.os.Build
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.plugins.constraints.R
import dagger.Lazy
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerUtilsImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rh: ResourceHelper,
    private val config: Lazy<Config>,
    private val receiverStatusStore: ReceiverStatusStore,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) : VersionCheckerUtils {

    private fun isConnected(): Boolean = receiverStatusStore.isConnected

    override fun triggerCheckVersion() {

        if (!sp.contains(R.string.key_last_successful_version_check_timestamp)) {
            // On a new installation, set it as 30 days old in order to warn that there is a new version.
            setLastCheckTimestamp(dateUtil.now() - TimeUnit.DAYS.toMillis(30))
        }

        // If we are good, only check once every day.
        if (dateUtil.now() > sp.getLong(R.string.key_last_successful_version_check_timestamp, 0) + CHECK_EVERY) {
            checkVersion()
        }
    }

    private fun checkVersion() = compareWithCurrentVersion("", config.get().VERSION_NAME)

    @Suppress("SameParameterValue")
    override fun compareWithCurrentVersion(newVersion: String?, currentVersion: String) {
        onSameVersionDetected()
    }

    private fun onOlderVersionDetected() {
        aapsLogger.debug(LTag.CORE, "Version newer than master. Are you developer?")
        setLastCheckTimestamp(dateUtil.now())
    }

    private fun onSameVersionDetected() {
        setLastCheckTimestamp(dateUtil.now())
    }

    private fun onVersionNotDetectable() {
        aapsLogger.debug(LTag.CORE, "Fetch failed")
    }

    private fun onNewVersionDetected(currentVersion: String, newVersion: String?) {
        val now = dateUtil.now()
        if (now > sp.getLong(R.string.key_last_versionchecker_warning, 0) + WARN_EVERY) {
            aapsLogger.debug(LTag.CORE, "Version $currentVersion outdated. Found $newVersion")
            uiInteraction.addNotification(Notification.NEW_VERSION_DETECTED, rh.gs(R.string.versionavailable, newVersion.toString()), Notification.LOW)
            sp.putLong(R.string.key_last_versionchecker_warning, now)
        }
    }

    private fun onExpireDateDetected(currentVersion: String, endDate: String?) {
        val now = dateUtil.now()
        if (now > sp.getLong(R.string.key_last_expired_versionchecker_warning, 0) + WARN_EVERY) {
            aapsLogger.debug(LTag.CORE, rh.gs(R.string.version_expire, currentVersion, endDate))
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.version_expire, currentVersion, endDate), Notification.LOW)
            sp.putLong(R.string.key_last_expired_versionchecker_warning, now)
        }
    }

    private fun setLastCheckTimestamp(timestamp: Long) {
        aapsLogger.debug(LTag.CORE, "Setting key_last_successful_version_check_timestamp ${dateUtil.dateAndTimeAndSecondsString(timestamp)}")
        sp.putLong(R.string.key_last_successful_version_check_timestamp, timestamp)
    }

    private fun String?.toNumberList() =
        this?.numericVersionPart().takeIf { !it.isNullOrBlank() }?.split(".")?.map { it.toInt() }

    override fun versionDigits(versionString: String?): IntArray {
        val digits = mutableListOf<Int>()
        versionString?.numericVersionPart().toNumberList()?.let {
            digits.addAll(it.take(4))
        }
        return digits.toIntArray()
    }

    override fun findVersion(file: String?): String? {
        val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
        return file?.lines()?.filter { regex.matches(it) }?.firstNotNullOfOrNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }
    }

    companion object {

        private val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)
        private val WARN_EVERY = TimeUnit.DAYS.toMillis(1)
    }
}

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1)
        ?: ""
/*
@Suppress("unused") fun findVersion(file: String?): String? {
    val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
    return file?.lines()?.filter { regex.matches(it) }?.firstNotNullOfOrNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }
}
*/
