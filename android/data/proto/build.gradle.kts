plugins {
  id("dqxn.kotlin.jvm")
  alias(libs.plugins.protobuf)
}

protobuf {
  protoc {
    artifact = libs.protoc.get().toString()
  }
  generateProtoTasks {
    all().configureEach {
      builtins {
        named("java") {
          option("lite")
        }
      }
      builtins {
        create("kotlin") {
          option("lite")
        }
      }
    }
  }
}

dependencies {
  api(libs.protobuf.kotlin.lite)
}
