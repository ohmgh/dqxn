plugins { id("dqxn.pack") }

android { namespace = "app.dqxn.android.pack.demo" }

dependencies {
  implementation(project(":pack:essentials:snapshots"))
  testImplementation(testFixtures(project(":sdk:contracts")))
}
