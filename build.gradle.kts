plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
    checkstyle
}

group = "dev.loki"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API of the server version the plugin runs on (Paper 26.1.2).
    // Compiling against the exact API keeps removed or renamed Bukkit members
    // (for example the enchantment constants) from breaking at runtime.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation("org.xerial:sqlite-jdbc:3.47.2.0")
    testCompileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testRuntimeOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("Loreport")
        relocate("okhttp3", "dev.loki.lorep.libs.okhttp3")
        relocate("okio", "dev.loki.lorep.libs.okio")
        relocate("com.zaxxer.hikari", "dev.loki.lorep.libs.hikari")
        // Don't relocate PostgreSQL to avoid driver loading issues
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    test {
        useJUnitPlatform()
    }
    
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
    
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

checkstyle {
    toolVersion = "10.21.4"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
