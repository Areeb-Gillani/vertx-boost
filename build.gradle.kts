plugins {
    id("java")
    id("maven-publish")
}
tasks.withType<KotlinCompile> { 
  compilerOptions.jvmTarget.set(JvmTarget.JVM_17) 
}

group = "io.github.areebgillani"
version = "0.0.1"
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.areebgillani"
            artifactId = rootProject.name
            version = "0.0.1"
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
