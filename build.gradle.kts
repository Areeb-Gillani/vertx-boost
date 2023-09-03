plugins {
    id("java")
    id("maven-publish")
}

group = "io.github.areebgillani"
version = "0.0.2"
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.areebgillani"
            artifactId = rootProject.name
            version = "0.0.2"
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    compileOnly("io.vertx:vertx-web:4.4.4")
}
