// Convenciones comunes del prototipo del experimento de Orquídea (reto 4).
// Cada servicio es una aplicación Spring Boot; services:comun es una librería y
// services:pki, una herramienta de línea de comandos que genera la PKI de prueba.
plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.dependency.management) apply false
}

val aplicaciones = setOf("recepcion", "carril", "monitor", "reprocesador", "simuladores")

configure(subprojects.filter { it.parent?.name == "services" }) {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = "co.mati.reto4"
    version = "0.1.0"

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports { mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    if (name in aplicaciones) {
        apply(plugin = "org.springframework.boot")
        dependencies {
            "implementation"(project(":services:comun"))
            "implementation"("org.springframework.boot:spring-boot-starter-actuator")
            "runtimeOnly"("io.micrometer:micrometer-registry-prometheus")
            "runtimeOnly"("org.postgresql:postgresql")
        }
        tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
            archiveFileName.set("app.jar")
        }
        tasks.named<Jar>("jar") { enabled = false }
    }
}
