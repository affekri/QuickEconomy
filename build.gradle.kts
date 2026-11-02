plugins {
    java
    alias(libs.plugins.run.paper)
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.4")
}

tasks {
    jar {
        enabled = false
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("com.fasterxml.jackson", "net.derfla.quickeconomy.libs.jackson")
        relocate("com.zaxxer.hikari", "net.derfla.quickeconomy.libs.hikari")
    }
    build {
        dependsOn(shadowJar)
    }
    assemble {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }
    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
