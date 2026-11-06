// The browser app's layout probe: the app's pure sources (test_inkwash.sh's list) with the pilot's shims, PilotC as main.
import org.teavm.gradle.api.JSModuleType
import org.teavm.gradle.api.OptimizationLevel

plugins { java; id("org.teavm") version "0.16.0" }
repositories { mavenCentral() }
dependencies { implementation(teavm.libs.jso) }

val repo = rootDir.resolve("../../../..").canonicalFile
val shimDir = rootDir.resolve("../src")
val inkwash = repo.resolve("scripts/test_inkwash.sh").readText()
val sources = Regex("^sources=\\((.*)\\)$", RegexOption.MULTILINE).find(inkwash)!!.groupValues[1].trim().split(Regex("\\s+")).toSet()
val shims = shimDir.walk().filter { it.name.endsWith(".java") }.map { it.nameWithoutExtension }.toSet()
sourceSets.main {
    java.setSrcDirs(listOf(shimDir, rootDir.resolve("src"), repo.resolve("app/src/main/java")))
    // The pilot's own mains (PilotA, PilotB) need its checks, so only its shims (org.teavm...) come from there.
    java.include { e -> e.isDirectory || e.file.startsWith(shimDir.resolve("org")) || e.file.startsWith(rootDir.resolve("src")) ||
        e.file.nameWithoutExtension.let { it in sources && it !in shims } }
}
tasks.withType<JavaCompile> { options.release.set(17); options.compilerArgs.add("-nowarn") }
teavm.js {
    mainClass.set("io.github.mpdairy.monopaint.PilotC"); moduleType.set(JSModuleType.COMMON_JS)
    obfuscated.set(true); optimization.set(OptimizationLevel.AGGRESSIVE); targetFileName.set("pilot-c.cjs")
}
teavm.wasmGC {
    mainClass.set("io.github.mpdairy.monopaint.PilotC")
    obfuscated.set(true); optimization.set(OptimizationLevel.AGGRESSIVE); targetFileName.set("pilot-c.wasm")
}
tasks.withType<org.teavm.gradle.tasks.TeaVMTask>().configureEach {
    val rest = classpath.from.toList(); classpath.setFrom(sourceSets.main.get().output, rest)
}
