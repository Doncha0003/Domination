plugins {
  kotlin("jvm") version "2.2.0"
  id("com.gradleup.shadow") version "8.3.0"
  id("xyz.jpenilla.run-paper") version "2.3.1"
  idea
  id("com.diffplug.spotless") version "7.0.3"
}

group = "com.tsubuserver"
version = "1.3.2"

repositories {
  mavenCentral()
  maven("https://maven.peco2282.com/repository/maven-public/")
  maven("https://repo.papermc.io/repository/maven-public/") {
    name = "papermc-repo"
  }
  maven("https://oss.sonatype.org/content/groups/public/") {
    name = "sonatype"
  }
  maven("https://repo.dmulloy2.net/repository/public/") {
    name = "dmulloy2-repo"
  }
  maven("https://repo.xenondevs.xyz/releases") {
    name = "xenondevs-repo"
  }
  maven("https://repo.onarandombox.com/content/groups/public") {
    name = "multiverseMultiverseReleases"
  }

  maven("https://maven.pkg.github.com/peco2282/PaperKit") {
    name = "GitHubPackages"
    credentials {
      username = System.getenv("GITHUB_ACTOR") ?: System.getProperty("GITHUB_ACTOR") ?: System.getenv("USERNAME")
      password = System.getenv("GITHUB_TOKEN") ?: System.getProperty("GITHUB_TOKEN") ?: System.getenv("TOKEN")
    }
  }
  maven("https://maven.pkg.github.com/Tsubu-Server/PlayerDataLib") {
    name = "GitHubPackages"
    credentials {
      username = System.getenv("GITHUB_ACTOR") ?: System.getProperty("GITHUB_ACTOR") ?: System.getenv("USERNAME")
      password = System.getenv("GITHUB_TOKEN") ?: System.getProperty("GITHUB_TOKEN") ?: System.getenv("TOKEN")
    }
  }
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
  implementation("org.joml:joml:1.10.5")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  compileOnly("com.cjcrafter:mechanicscore:4.1.2")
  compileOnly("com.cjcrafter:weaponmechanics:4.1.2")
//  compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
  implementation("xyz.xenondevs.invui:invui:1.45")
  implementation("xyz.xenondevs.invui:invui-kotlin:1.45")
  compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.0.2")
  implementation("fr.mrmicky:fastboard:2.1.5")
  implementation("com.github.peco2282:paperkit:1.0.0")
  implementation("commons-io:commons-io:2.20.0")
  compileOnly("com.tsubuserver:playerdatalib-api:1.1.0-beta2")
}

tasks {
  runServer {
    // Configure the Minecraft version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    minecraftVersion("1.21.4")
  }
}

val targetJavaVersion = 21
kotlin {
  jvmToolchain(targetJavaVersion)
}

tasks.build {
  dependsOn("shadowJar")
}
tasks.shadowJar {
  relocate("org.jetbrains.annotations", "com.tsubuserver.zonerush.libs.org.jetbrains.annotations")
  relocate("org.apache.commons", "com.tsubuserver.zonerush.libs.org.apache.commons")
  relocate("kotlin", "com.tsubuserver.zonerush.libs.kotlin")
  relocate("xyz.xenondevs", "com.tsubuserver.zonerush.libs.xyz.xenondevs")
  relocate("fr.mrmicky.fastboard", "com.tsubuserver.zonerush.libs.fr.mrmicky.fastboard")
  relocate("com.github.peco2282", "com.tsubuserver.zonerush.libs.com.github.peco2282")
}

tasks.processResources {
  val props = mapOf("version" to version)
  inputs.properties(props)
  filteringCharset = "UTF-8"
  filesMatching("plugin.yml") {
    expand(props)
  }
}

idea {
  module {
    isDownloadSources = true
    isDownloadJavadoc = true
  }
}

spotless {
  kotlin {
    ktlint()
    trimTrailingWhitespace()
    endWithNewline()
    leadingTabsToSpaces(2)
    target("src/**/*.kt")
    suppressLintsFor {
      step = "ktlint"
      shortCode = "standard:no-wildcard-imports"
    }
  }
}
