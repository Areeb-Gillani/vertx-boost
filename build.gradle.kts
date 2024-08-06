plugins {
    id("java")
    id("maven-publish")
}

group = "io.github.areebgillani"
version = "0.0.11"
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.areebgillani"
            artifactId = rootProject.name
            version = "0.0.11"
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
}

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    compileOnly("io.vertx:vertx-web:4.4.5")
    implementation("io.vertx:vertx-config:4.4.5")
}
