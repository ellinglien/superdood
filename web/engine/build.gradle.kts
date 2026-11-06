// The app's engine compiled to WebAssembly (GC) by TeaVM: the pure Java engine as the desktop checks compile it
// (scripts/test_inkwash.sh's sources), WebParity's lay (the Manta's way of laying a received change), the shims in
// shims/ standing in for the java.util.concurrent classes TeaVM lacks (TeaVM maps T-classes by name), and WebEngine's
// exports. bash web/engine/build.sh builds it into web/engine/out/. TeaVM's JS target computes float as double (it
// drifts from the app); WebAssembly has real f32 and matches the JVM (docs/brush-lab/teavm-pilot/NOTES.md).
import org.teavm.gradle.api.OptimizationLevel

plugins {
    java
    id("org.teavm") version "0.16.0"
}
repositories { mavenCentral() }
dependencies { implementation(teavm.libs.jso) }

val repo = rootDir.resolve("../..").canonicalFile
val inkwash = repo.resolve("scripts/test_inkwash.sh").readText()
fun list(name: String) = Regex("^$name=\\((.*)\\)$", RegexOption.MULTILINE).find(inkwash)!!.groupValues[1].trim().split(Regex("\\s+")).toSet()
val sources = list("sources") + "WebParity"
val own = listOf(file("src"), file("shims"))
val shims = file("shims").walk().filter { it.name.endsWith(".java") }.map { it.nameWithoutExtension }.toSet()
sourceSets.main {
    java.setSrcDirs(own + listOf(repo.resolve("app/src/main/java"), repo.resolve("app/src/test/java")))
    java.include { e -> e.isDirectory || own.any { e.file.startsWith(it) } || e.file.nameWithoutExtension.let { it in sources && it !in shims } }
}
tasks.withType<JavaCompile> { options.release.set(17); options.compilerArgs.add("-nowarn") }

teavm.wasmGC {
    mainClass.set("io.github.mpdairy.monopaint.WebEngine")
    obfuscated.set(true)
    optimization.set(OptimizationLevel.AGGRESSIVE)
    targetFileName.set("mbd-engine.wasm")
}
// The shims ahead of TeaVM's own classes, so TRuntime (with maxMemory) stands in for TeaVM's.
tasks.withType<org.teavm.gradle.tasks.TeaVMTask>().configureEach {
    val rest = classpath.from.toList()
    classpath.setFrom(sourceSets.main.get().output, rest)
}
