package app.dqxn.android.sdk.contracts.setup

import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SetupDefinitionTest {

  @Nested
  @DisplayName("Construction tests (7 subtypes)")
  inner class ConstructionTests {

    @Test
    fun `RuntimePermission constructs with permissions list`() {
      val def =
        SetupDefinition.RuntimePermission(
          id = "camera",
          label = "Camera Access",
          permissions = listOf("android.permission.CAMERA"),
        )

      assertThat(def.id).isEqualTo("camera")
      assertThat(def.label).isEqualTo("Camera Access")
      assertThat(def.permissions).containsExactly("android.permission.CAMERA")
      assertThat(def.minSdk).isEqualTo(0)
    }

    @Test
    fun `SystemServiceToggle constructs with settingsAction and serviceType`() {
      val def =
        SetupDefinition.SystemServiceToggle(
          id = "bluetooth",
          label = "Enable Bluetooth",
          settingsAction = "android.settings.BLUETOOTH_SETTINGS",
          serviceType = ServiceType.BLUETOOTH,
        )

      assertThat(def.settingsAction).isEqualTo("android.settings.BLUETOOTH_SETTINGS")
      assertThat(def.serviceType).isEqualTo(ServiceType.BLUETOOTH)
    }

    @Test
    fun `SystemService constructs with serviceType`() {
      val def =
        SetupDefinition.SystemService(
          id = "wifi",
          label = "WiFi Required",
          serviceType = ServiceType.WIFI,
        )

      assertThat(def.serviceType).isEqualTo(ServiceType.WIFI)
    }

    @Test
    fun `DeviceScan constructs with handlerId and optional fields`() {
      val def =
        SetupDefinition.DeviceScan(
          id = "ble-scan",
          label = "Scan for Device",
          handlerId = "sg-erp2-handler",
          deviceNamePattern = "ERP2.*",
          serviceUuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"),
          maxDevices = 3,
        )

      assertThat(def.handlerId).isEqualTo("sg-erp2-handler")
      assertThat(def.deviceNamePattern).isEqualTo("ERP2.*")
      assertThat(def.serviceUuids).hasSize(1)
      assertThat(def.maxDevices).isEqualTo(3)
    }

    @Test
    fun `Instruction constructs with stepNumber and optional action`() {
      val def =
        SetupDefinition.Instruction(
          id = "step1",
          label = "Connect cable",
          stepNumber = 1,
          action = InstructionAction.OpenUrl("https://docs.example.com"),
        )

      assertThat(def.stepNumber).isEqualTo(1)
      assertThat(def.action).isInstanceOf(InstructionAction.OpenUrl::class.java)
    }

    @Test
    fun `Info constructs with style`() {
      val def =
        SetupDefinition.Info(
          id = "warning",
          label = "Battery Warning",
          style = InfoStyle.WARNING,
        )

      assertThat(def.style).isEqualTo(InfoStyle.WARNING)
    }

    @Test
    fun `Setting constructs wrapping a SettingDefinition`() {
      val inner =
        SettingDefinition.BooleanSetting(
          key = "autoConnect",
          label = "Auto Connect",
          default = true,
        )
      val def = SetupDefinition.Setting(definition = inner)

      assertThat(def.definition).isEqualTo(inner)
      assertThat(def.id).isEqualTo("autoConnect")
      assertThat(def.label).isEqualTo("Auto Connect")
    }
  }

  @Nested
  @DisplayName("Category classification")
  inner class CategoryTests {

    @Test
    fun `isRequirement true for RuntimePermission, SystemServiceToggle, SystemService, DeviceScan`() {
      val permission =
        SetupDefinition.RuntimePermission(
          id = "p",
          label = "P",
          permissions = listOf("android.permission.CAMERA"),
        )
      val toggle =
        SetupDefinition.SystemServiceToggle(
          id = "t",
          label = "T",
          settingsAction = "action",
          serviceType = ServiceType.BLUETOOTH,
        )
      val service =
        SetupDefinition.SystemService(
          id = "s",
          label = "S",
          serviceType = ServiceType.LOCATION,
        )
      val scan =
        SetupDefinition.DeviceScan(
          id = "d",
          label = "D",
          handlerId = "h",
        )

      assertThat(permission.isRequirement).isTrue()
      assertThat(toggle.isRequirement).isTrue()
      assertThat(service.isRequirement).isTrue()
      assertThat(scan.isRequirement).isTrue()
    }

    @Test
    fun `isDisplay true for Instruction, Info`() {
      val instruction =
        SetupDefinition.Instruction(
          id = "i",
          label = "I",
          stepNumber = 1,
        )
      val info = SetupDefinition.Info(id = "n", label = "N")

      assertThat(instruction.isDisplay).isTrue()
      assertThat(info.isDisplay).isTrue()
    }

    @Test
    fun `isInput true for Setting`() {
      val inner = SettingDefinition.BooleanSetting(key = "k", label = "L", default = false)
      val setting = SetupDefinition.Setting(definition = inner)

      assertThat(setting.isInput).isTrue()
    }

    @Test
    fun `requirement types are not display or input`() {
      val permission =
        SetupDefinition.RuntimePermission(
          id = "p",
          label = "P",
          permissions = listOf("android.permission.CAMERA"),
        )

      assertThat(permission.isRequirement).isTrue()
      assertThat(permission.isDisplay).isFalse()
      assertThat(permission.isInput).isFalse()
    }
  }

  @Nested
  @DisplayName("Setting wrapper delegation")
  inner class SettingWrapperTests {

    @Test
    fun `Setting wrapper delegates id from inner definition`() {
      val inner =
        SettingDefinition.BooleanSetting(key = "myKey", label = "My Label", default = true)
      val setting = SetupDefinition.Setting(definition = inner)

      assertThat(setting.id).isEqualTo("myKey")
    }

    @Test
    fun `Setting wrapper delegates label from inner definition`() {
      val inner =
        SettingDefinition.BooleanSetting(key = "k", label = "Custom Label", default = true)
      val setting = SetupDefinition.Setting(definition = inner)

      assertThat(setting.label).isEqualTo("Custom Label")
    }

    @Test
    fun `Setting wrapper can override delegated fields`() {
      val inner = SettingDefinition.BooleanSetting(key = "k", label = "Inner", default = true)
      val setting =
        SetupDefinition.Setting(definition = inner, id = "override-id", label = "Override Label")

      assertThat(setting.id).isEqualTo("override-id")
      assertThat(setting.label).isEqualTo("Override Label")
    }
  }

  @Nested
  @DisplayName("asSetup() extension")
  inner class AsSetupTests {

    @Test
    fun `SettingDefinition asSetup() wraps in SetupDefinition Setting`() {
      val inner =
        SettingDefinition.IntSetting(
          key = "brightness",
          label = "Brightness",
          default = 50,
          min = 0,
          max = 100,
        )

      val setup = inner.asSetup()

      assertThat(setup).isInstanceOf(SetupDefinition.Setting::class.java)
      assertThat(setup.definition).isEqualTo(inner)
      assertThat(setup.id).isEqualTo("brightness")
    }
  }

  @Nested
  @DisplayName("getDefaultValue()")
  inner class GetDefaultValueTests {

    @Test
    fun `returns correct default for BooleanSetting wrapper`() {
      val inner = SettingDefinition.BooleanSetting(key = "k", label = "l", default = true)
      val setup = SetupDefinition.Setting(definition = inner)

      assertThat(setup.getDefaultValue()).isEqualTo(true)
    }

    @Test
    fun `returns correct default for IntSetting wrapper`() {
      val inner =
        SettingDefinition.IntSetting(key = "k", label = "l", default = 42, min = 0, max = 100)
      val setup = SetupDefinition.Setting(definition = inner)

      assertThat(setup.getDefaultValue()).isEqualTo(42)
    }

    @Test
    fun `returns Unit for InstructionSetting wrapper`() {
      val inner = SettingDefinition.InstructionSetting(key = "k", label = "l")
      val setup = SetupDefinition.Setting(definition = inner)

      assertThat(setup.getDefaultValue()).isEqualTo(Unit)
    }

    @Test
    fun `returns null for requirement subtypes`() {
      val permission =
        SetupDefinition.RuntimePermission(
          id = "p",
          label = "P",
          permissions = listOf("android.permission.CAMERA"),
        )

      assertThat(permission.getDefaultValue()).isNull()
    }

    @Test
    fun `returns null for display subtypes`() {
      val instruction = SetupDefinition.Instruction(id = "i", label = "I", stepNumber = 1)
      val info = SetupDefinition.Info(id = "n", label = "N")

      assertThat(instruction.getDefaultValue()).isNull()
      assertThat(info.getDefaultValue()).isNull()
    }
  }
}
