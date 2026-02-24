plugins { id("dqxn.pack") }

android { namespace = "app.dqxn.android.pack.essentials" }

dependencies {
  implementation(project(":pack:essentials:snapshots"))
  implementation(libs.play.services.location)
  testImplementation(testFixtures(project(":sdk:contracts")))
}
