plugins {
    id("java")
    id("maven-publish")
}

group = "io.github.Areeb-Gillani"
version = "0.1.0"

val vertxVersion = "4.5.9"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = rootProject.name
            version = project.version.toString()
            from(components["java"])
        }
    }
}


tasks.publishToMavenLocal {
    dependsOn(tasks.build)
    onlyIf {
        true
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    compileOnly("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-config:$vertxVersion")
}
