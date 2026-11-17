package com.chaosconnect.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.ui.PackageInstallState
import com.chaosconnect.app.ui.page.md3.Md3PackageInstall
import com.chaosconnect.app.ui.page.miuix.MiuixPackageInstall
import com.chaosconnect.app.ui.theme.AppTheme
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageUiTest {
 @get:Rule val ui=createComposeRule()
 @Test fun bothStylesRequireConfirmationWithTheInstalledName(){
  var miuix by mutableStateOf(true);var state by mutableStateOf(PackageInstallState(name="文楷",kind="字体包",files=1,total=1024));var selected=0;var installed=0
  ui.setContent{AppTheme(AppSettings(useMiuix=miuix,dynamicColor=false)){
   val ask={state=state.copy(confirming=true)};val confirm={installed++;state=state.copy(confirming=false,busy=true,message="请在手环确认安装投递包：文楷")};val cancel={state=state.copy(confirming=false)}
   if(miuix)MiuixPackageInstall(state,true,{selected++},ask,confirm,cancel,{},{},{})
   else Md3PackageInstall(state,true,{selected++},ask,confirm,cancel,{},{},{})
  }}
  for(style in listOf(true,false)){
   ui.runOnIdle{miuix=style;state=state.copy(busy=false,confirming=false)}
   ui.onNodeWithTag("package_choose").performScrollTo().performClick()
   ui.onNodeWithTag("package_install").performScrollTo().performClick()
   ui.onNodeWithTag("package_confirmation").assertTextEquals("是否安装投递包：文楷")
   ui.onNodeWithTag("package_cancel_confirmation").performClick();ui.runOnIdle{assertEquals(if(style)0 else 1,installed)}
   ui.onNodeWithTag("package_install").performScrollTo().performClick();ui.onNodeWithTag("package_confirm").performClick()
   ui.onNodeWithTag("package_stop").performScrollTo().assertIsDisplayed()
  }
  ui.runOnIdle{assertEquals(2,selected);assertEquals(2,installed)}
 }
}
