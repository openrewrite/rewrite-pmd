@file:Suppress("UnstableApiUsage")


plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-proprietary-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "OpenRewrite recipes for managing PMD configuration."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    provided("org.openrewrite:rewrite-xml:${rewriteVersion}")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop", "dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
