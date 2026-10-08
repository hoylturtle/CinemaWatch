package com.cinemawatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cinemawatch.data.CinemaAsset
import com.cinemawatch.radio.LiveRadio

@Composable internal fun ManualAssetDialog(asset: CinemaAsset?, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(asset?.name.orEmpty()) }
    var radio by remember { mutableStateOf("WIFI") }
    var address by remember { mutableStateOf("") }
    var authorized by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(if (asset == null) R.string.create_asset else R.string.bind_signal)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (asset == null) OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.asset_name)) }, modifier = Modifier.testTag("asset-name"), singleLine = true, enabled = !saving)
            else Text(asset.name)
            Text(stringResource(R.string.manual_asset_note))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("WIFI" to "Wi-Fi", "BLE" to "BLE").forEach { (kind, label) -> FilterChip(selected = radio == kind, onClick = { radio = kind }, label = { Text(label) }, enabled = !saving) }
            }
            OutlinedTextField(address, { address = it.take(24) }, label = { Text(stringResource(R.string.radio_address)) }, supportingText = { Text(stringResource(R.string.address_hint)) }, singleLine = true, modifier = Modifier.testTag("radio-address"), enabled = !saving)
            AuthorizationCheck(authorized, { authorized = it }, !saving)
        }
    }, confirmButton = { Button(onClick = { onSave(name, radio, address) }, enabled = !saving && authorized && name.isNotBlank() && (asset == null || address.isNotBlank())) { Text(stringResource(if (saving) R.string.saving else R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun SignalBindingDialog(radio: LiveRadio, assets: List<CinemaAsset>, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(radio.name) }
    var authorized by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.register)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${radio.kind} · ${radio.address}")
            Text(stringResource(R.string.binding_notice))
            TextButton(onClick = { selected = null }, enabled = !saving) { RadioButton(selected == null, null); Text(stringResource(R.string.new_asset_option)) }
            assets.forEach { asset -> TextButton(onClick = { selected = asset.id }, enabled = !saving) { RadioButton(selected == asset.id, null); Text(asset.name) } }
            if (selected == null) OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.asset_name)) }, modifier = Modifier.testTag("asset-name"), enabled = !saving)
            AuthorizationCheck(authorized, { authorized = it }, !saving)
        }
    }, confirmButton = { Button(onClick = { onSave(name, selected) }, enabled = !saving && authorized && (selected != null || name.isNotBlank())) { Text(stringResource(if (saving) R.string.saving else R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun AuthorizationCheck(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled, modifier = Modifier.testTag("authorization"))
        Text(stringResource(R.string.authorized_confirm))
    }
}
