// The TeaVM pilot (roadmap step 6): the app's pure Java engine, as the desktop checks compile it (test_inkwash.sh's
// sources), with the pilot's shims in src/ standing in for the classes of the same name, compiled to JavaScript.
//   ../../../gradlew -p docs/brush-lab/teavm-pilot build   (or run.sh, which also runs the comparisons)
import org.teavm.gradle.api.JSModuleType
import org.teavm.gradle.api.OptimizationLevel

plugins {
    java
    id("org.teavm") version "0.16.0"
}
repositories { mavenCentral() }
dependencies { implementation(teavm.libs.jso) }

val repo = rootDir.resolve("../../..").canonicalFile
val app = repo.resolve("app/src/main/java")
val pilot = file("src")
// The shims (java.util.concurrent run inline, Runtime.maxMemory, one core): web/engine's, the engine's real build.
val shimDir = repo.resolve("web/engine/shims")
val inkwash = repo.resolve("scripts/test_inkwash.sh").readText()
fun list(name: String) = Regex("^$name=\\((.*)\\)$", RegexOption.MULTILINE).find(inkwash)!!.groupValues[1].trim().split(Regex("\\s+")).toSet()
val sources = list("sources")
// The checks compile with the engine (CodeSpecChecks is the spec's writer); TeaVM keeps only what the exports reach.
val HARNESS = list("checks") + list("helpers") + "WebParity"
val shims = shimDir.walk().filter { it.name.endsWith(".java") }.map { it.nameWithoutExtension }.toSet()
sourceSets.main {
    java.setSrcDirs(listOf(pilot, shimDir, app, repo.resolve("app/src/test/java")))
    // The app's listed sources but those the pilot shims; WebParity (the harness's app half) from the tests; the pilot's own.
    java.include { e -> e.isDirectory || e.file.startsWith(pilot) || e.file.startsWith(shimDir) ||
        e.file.nameWithoutExtension.let { (it in sources || it in HARNESS) && it !in shims } }
}
tasks.withType<JavaCompile> { options.release.set(17); options.compilerArgs.add("-nowarn") }

// -Ppilot=A builds Pilot A (code reading and words), else Pilot B (the stroke engine); run.sh builds both.
val which = (findProperty("pilot") ?: "B").toString()
teavm.js {
    mainClass.set("io.github.mpdairy.monopaint.Pilot$which")
    moduleType.set(JSModuleType.COMMON_JS)
    obfuscated.set(true)
    optimization.set(OptimizationLevel.AGGRESSIVE)
    targetFileName.set("pilot-${which.lowercase()}.cjs")
}
// The same as WebAssembly (GC): Java's float arithmetic as Java does it, which TeaVM's JS doesn't (it computes floats as doubles).
teavm.wasmGC {
    mainClass.set("io.github.mpdairy.monopaint.Pilot$which")
    obfuscated.set(true)
    optimization.set(OptimizationLevel.AGGRESSIVE)
    targetFileName.set("pilot-${which.lowercase()}.wasm")
}
// The pilot's classes ahead of TeaVM's own, so its TRuntime (with maxMemory) stands in for TeaVM's.
tasks.withType<org.teavm.gradle.tasks.TeaVMTask>().configureEach {
    val rest = classpath.from.toList()
    classpath.setFrom(sourceSets.main.get().output, rest)
}
