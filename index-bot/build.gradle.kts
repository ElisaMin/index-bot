import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.1"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.spring") version "1.8.21"
}

group = "com.tgse"
version = "2.0.0-next"
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
repositories {
    mavenCentral {
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
    }
    maven {
        url = uri("https://snapshots.elastic.co/maven/")
    }
    maven {
        name = "lucene-snapshots"
        url = uri("https://s3.amazonaws.com/download.elasticsearch.org/lucenesnapshots/83f9835")
    }
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-reflect")
   implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

   implementation("org.springframework.boot:spring-boot-starter")
   annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

   implementation("com.google.guava:guava:31.1-jre")
   implementation("org.dom4j:dom4j:2.1.4")
   implementation("org.jsoup:jsoup:1.16.1")
   implementation("org.apache.commons:commons-text:1.10.0")
   implementation("io.reactivex.rxjava3:rxjava:3.1.6")

   // telegram
   api("com.github.pengrad:java-telegram-bot-api:6.7.0") {
       exclude(group = "com.google.code.gson")
   }
   implementation("com.google.code.gson:gson:2.10.1")
   // await status
   implementation("io.github.pcmind:leveldb:1.2")
   // elasticsearch
   val esv = System.getenv("elasticsearch_version") ?: "7.17.10"
   implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:$esv")

   testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
   useJUnitPlatform()
}
