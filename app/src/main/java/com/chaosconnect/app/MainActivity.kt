package com.chaosconnect.app

import android.os.Bundle
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaosconnect.app.ui.ShellApp
import com.chaosconnect.app.ui.ShellViewModel
import com.chaosconnect.app.ui.rememberShellNavigation
import com.chaosconnect.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ShellViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val devices by vm.devices.collectAsStateWithLifecycle()
            val protocol by vm.protocol.collectAsStateWithLifecycle()
            val bluetooth by vm.bluetoothState.collectAsStateWithLifecycle()
            val browser by vm.browser.collectAsStateWithLifecycle()
            val terminal by vm.terminal.collectAsStateWithLifecycle()
            val speedTest by vm.speedTest.collectAsStateWithLifecycle()
            val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result ->
                if(result.values.all{it})vm.scanBluetooth() else vm.bluetoothPermissionDenied()
            }
            val packageInstall by vm.packageInstall.collectAsStateWithLifecycle()
            val packagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.selectPackage(uri)}
            val protocolCheck by vm.protocolCheck.collectAsStateWithLifecycle()
            val navigation = rememberShellNavigation()
            settings?.let { current ->
                AppTheme(current) { ShellApp(current, devices, vm::update, navigation,protocol,protocolCheck,vm::checkProtocol,vm::disconnectProtocol,bluetooth,{permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT))},vm::connectBluetooth,vm::requestDeveloper,
                    browser,{vm.loadDirectory()},{vm.loadDirectory(browser.parent)},{vm.loadDirectory(append=true)},vm::openFile,terminal,vm::startTerminal,vm::stopTerminal,vm::sendTerminal,vm::interruptTerminal,vm::clearTerminal,vm::terminalVisible,speedTest,vm::startSpeedTest,vm::stopSpeedTest,packageInstall,{packagePicker.launch(arrayOf("*/*"))},vm::askPackageInstall,vm::installPackage,vm::dismissPackageInstall,vm::stopPackageInstall) }
            }
        }
    }
}
