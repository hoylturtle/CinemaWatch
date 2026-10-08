package com.cinemawatch

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemawatch.update.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable internal fun UpdateCard(app: CinemaApp, inspectionBusy: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = app.updates
    val state by controller.state.collectAsStateWithLifecycle()
    var permissionNotice by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    val inProgress = installing || state.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING)
    fun install() {
        val ready = controller.state.value
        val file = ready.file ?: return
        val release = ready.release ?: return
        if (inspectionBusy || installing) return
        scope.launch {
            installing = true
            try {
                GithubUpdates.validateDownloaded(context, file, release)
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").apply {
                    clipData = ClipData.newRawUri("CinemaWatch update", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { controller.reject((e as? UpdateException)?.reason ?: UpdateError.INVALID) }
            finally { installing = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) { permissionNotice = false; install() }
        else permissionNotice = true
    }
    Panel {
        SectionTitle(R.string.update_title)
        Text(stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.update_network_note), style = MaterialTheme.typography.bodySmall)
        if (inspectionBusy) Text(stringResource(R.string.update_wait_scan))
        Button(onClick = { permissionNotice = false; controller.check() }, enabled = !inspectionBusy && !inProgress,
            modifier = Modifier.testTag("check-update")) { Text(stringResource(if (state.phase == UpdatePhase.CHECKING) R.string.update_checking else R.string.check_update)) }
        when (state.phase) {
            UpdatePhase.CURRENT -> Text(stringResource(R.string.update_latest), modifier = Modifier.testTag("update-latest"))
            UpdatePhase.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY -> {
                state.release?.let { release ->
                    Text(stringResource(R.string.update_available, release.versionName), modifier = Modifier.testTag("update-available"))
                    if (release.notes.isNotBlank()) Text(release.notes)
                    Text(stringResource(R.string.update_keeps_data), style = MaterialTheme.typography.bodySmall)
                }
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_progress, state.percent))
                } else if (state.phase == UpdatePhase.READY) {
                    Text(stringResource(R.string.update_verified))
                    Button(onClick = {
                        if (context.packageManager.canRequestPackageInstalls()) install()
                        else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
                    }, enabled = !inspectionBusy && !installing, modifier = Modifier.testTag("install-update")) { Text(stringResource(R.string.update_install)) }
                } else Button(onClick = { controller.download() }, enabled = !inspectionBusy, modifier = Modifier.testTag("download-update")) { Text(stringResource(R.string.update_download)) }
            }
            UpdatePhase.ERROR -> Text(stringResource(when (state.error) {
                UpdateError.SIGNATURE -> R.string.update_signature_error
                UpdateError.STORAGE -> R.string.update_storage_error
                UpdateError.INVALID -> R.string.update_invalid_error
                else -> R.string.update_network_error
            }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("update-error"))
            UpdatePhase.IDLE -> Unit
        }
        if (permissionNotice) Text(stringResource(R.string.update_install_permission))
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))) }) { Text(stringResource(R.string.update_release_page)) }
    }
}
