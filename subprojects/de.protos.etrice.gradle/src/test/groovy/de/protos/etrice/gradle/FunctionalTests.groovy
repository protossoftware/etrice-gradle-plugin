package de.protos.etrice.gradle

import org.junit.jupiter.api.*
import org.gradle.testkit.runner.TaskOutcome

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Execution(ExecutionMode.CONCURRENT)
public class FunctionalTests {

static final ConcurrentHashMap<String, Long> __ftStart = new ConcurrentHashMap<>()
static final ConcurrentHashMap<String, Long> __ftDurations = new ConcurrentHashMap<>()
static final AtomicLong __ftTotal = new AtomicLong(0L)

@BeforeEach
void __ftBeforeEach(TestInfo info) {
    def name = info.displayName
    __ftStart.put(name, System.nanoTime())
}

@AfterEach
void __ftAfterEach(TestInfo info) {
    def name = info.displayName
    Long s = __ftStart.remove(name)
    if (s != null) {
        long dMs = (System.nanoTime() - s) / 1_000_000L
        __ftDurations.put(name, dMs)
        __ftTotal.addAndGet(dMs)
        println("[FT_PROFILE] TEST '" + name + "' took " + dMs + " ms")
    }
}

@AfterAll
static void __ftAfterAll() {
    if (!__ftDurations.isEmpty()) {
        def sorted = __ftDurations.entrySet().toList().sort { -it.value }
        println("[FT_PROFILE] ===== FunctionalTests Summary =====")
        println(String.format("[FT_PROFILE] Total %d tests, cumulative %d ms; longest:", __ftDurations.size(), __ftTotal.get()))
        int topN = Math.min(5, sorted.size())
        for (int i = 0; i < topN; i++) {
            def e = sorted[i]
            println(String.format("[FT_PROFILE] #%d %s : %d ms", i+1, e.key, e.value))
        }
        def fastHint = System.getenv('FT_FAST') ?: System.getProperty('ft.fast')
        if (!('true'.equalsIgnoreCase(fastHint?.toString()))) {
            println("[FT_PROFILE] Hint: Enable faster nested builds by setting FT_FAST=true or -Dft.fast=true")
        }
    }
}

def etriceVersion = "5.4.0"
def repositories = """\
repositories {
	maven {
		url = 'https://repo.eclipse.org/content/repositories/maven_central/'
	}
	maven {
		url = 'https://repo.eclipse.org/content/repositories/etrice/'
	}
}"""

@Test
void "build empty eTrice project"() {
def buildFile = """\
plugins {
	id 'de.protos.etrice-base'
}
modelSet {
	test
}"""
GradleProjectBuilder.build("etriceEmptyProjectTest") {
	write("build.gradle", buildFile)
	gradle("generate") {
		assert task(":generateTest")?.outcome == TaskOutcome.NO_SOURCE
	}
}}

@Test
void "generate basic eTrice C project with modellib from model library plugin"() {
def buildFile = """\
plugins {
	id 'de.protos.etrice-c'
	id 'de.protos.model-library'
}
${repositories}
dependencies {
	generator 'org.eclipse.etrice:org.eclipse.etrice.generator.c:${etriceVersion}'
	modelLibrary 'org.eclipse.etrice:org.eclipse.etrice.modellib.c:${etriceVersion}'
}
modelSet {
	room {
		modelpath.from unzipModel.destination
	}
}"""
def roomFile = """\
RoomModel test {
	import etrice.api.annotations.TestInstance
	ActorClass ATest {
		@TestInstance
	}
}"""
GradleProjectBuilder.build("etriceCTest") {
	write("build.gradle", buildFile)
	write("model/test.room", roomFile)
	gradle("build") {
		assert task(":generateRoom")?.outcome == TaskOutcome.SUCCESS
		assert exists("build/src-gen/room/test/ATest.c")
	}
}}

@Test
void "build basic eTrice Java project with modellib from repository"() {
def buildFile = """\
plugins {
	id 'java-library'
	id 'de.protos.etrice-java'
}
${repositories}
configurations {
	implementation.extendsFrom modelpath
}
dependencies {
	generator 'org.eclipse.etrice:org.eclipse.etrice.generator.java:${etriceVersion}'
	modelpath 'org.eclipse.etrice:org.eclipse.etrice.modellib.java:${etriceVersion}'
	implementation 'org.eclipse.etrice:org.eclipse.etrice.runtime.java:${etriceVersion}'
}
sourceSets.main.java.srcDir modelSet.room.genDir"""
def roomFile = """\
RoomModel test {
	import etrice.api.annotations.TestInstance
	ActorClass ATest {
		@TestInstance
	}
}"""
GradleProjectBuilder.build("etriceJavaTest") {
	write("build.gradle", buildFile)
	write("model/test.room", roomFile)
	gradle("build") {
		assert task(":generateRoom")?.outcome == TaskOutcome.SUCCESS
		assert task(":compileJava")?.outcome == TaskOutcome.SUCCESS
		assert exists("build/src-gen/room/test/ATest.java")
	}
}}

@Test
void "generate multi project eTrice C project"() {
def rootBuildFile = """\
plugins {
	id 'de.protos.etrice-c' apply false
}
subprojects {
	apply plugin: 'de.protos.etrice-c'
	${repositories}
	dependencies {
		generator 'org.eclipse.etrice:org.eclipse.etrice.generator.c:${etriceVersion}'
	}
}"""
def libBuildFile = """\
dependencies {
	modelpath 'org.eclipse.etrice:org.eclipse.etrice.modellib.c:${etriceVersion}'
}"""
def appBuildFile = """\
dependencies {
	modelpath project(':lib')
}
"""
def libRoomFile = """\
RoomModel lib {
	import etrice.api.types.uint32
	ActorClass ALib {
		Structure {
			Attribute i32 : uint32
		}
	}
}"""
def appRoomFile = """\
RoomModel app {
	import etrice.api.types.boolean
	ActorClass AApp {
		Structure {
			Attribute b : boolean
			ActorRef aref : lib.ALib
		}
	}
}"""
GradleProjectBuilder.build("etriceMultiProjectTest") {
	write("settings.gradle", "include 'lib', 'app'")
	write("build.gradle", rootBuildFile)
	write("lib/build.gradle", libBuildFile)
	write("lib/model/lib.room", libRoomFile)
	write("app/build.gradle", appBuildFile)
	write("app/model/app.room", appRoomFile)
	gradle("build") {
		assert task(":lib:generateRoom")?.outcome == TaskOutcome.SUCCESS
		assert task(":app:generateRoom")?.outcome == TaskOutcome.SUCCESS
		assert exists("lib/build/src-gen/room/lib/ALib.c")
		assert exists("app/build/src-gen/room/app/AApp.c")
	}
}}

@Test
void "zip and unzip source"() {
def libBuildFile = """\
plugins {
    id 'de.protos.source-publish'
}
zipSource.from 'src'"""
def appBuildFile = """\
plugins {
	id 'de.protos.source-library'
}
${repositories}
dependencies {
    sourceLibrary project(':lib')
}"""
def sourceFile = """
int foo() {
	return 2;
}"""
GradleProjectBuilder.build("etriceSourceZipUnzipTest") {
	write("settings.gradle", "include 'lib', 'app'")
	write("lib/build.gradle", libBuildFile)
	write("lib/src/test.c", sourceFile)
	write("app/build.gradle", appBuildFile)
	gradle(":lib:zipSource") {
		assert task(":lib:zipSource")?.outcome == TaskOutcome.SUCCESS
	}
	gradle("unzipSource") {
		assert task(":app:unzipSource")?.outcome == TaskOutcome.SUCCESS
		assert exists("app/build/sourcelib/test.c")
	}
}}

@Test
void "convert etunit files"() {
def buildFile = """\
plugins {
	id 'de.protos.etunit-convert'
}
${repositories}
etunitConvert {
	convertTestResults {
		source 'log'
	}
}"""
def etuFile = """\
etUnit report
ts start: etUnit
tc start 11: openAll and closeAll
tc end 11: 0"""
GradleProjectBuilder.build("etunitConvertTest") {
	write("build.gradle", buildFile)
	gradle("convertTestResults") {
		assert task(":convertTestResults")?.outcome == TaskOutcome.NO_SOURCE
	}
	write("log/test1.etu", etuFile)
	gradle("convertTestResults") {
		assert task(":convertTestResults")?.outcome == TaskOutcome.SUCCESS
		assert exists("log/test1.xml")
	}
	write("log/test2.etu", etuFile)
	gradle("convertTestResults") {
		assert task(":convertTestResults")?.outcome == TaskOutcome.SUCCESS
		assert exists("log/test2.xml")
	}
}}

@Test
void "snapshot minimal C generation"() {
def buildFile = """\
plugins {
	id 'de.protos.etrice-c'
}
${repositories}
dependencies {
	generator 'org.eclipse.etrice:org.eclipse.etrice.generator.c:${etriceVersion}'
}
modelSet {
	room
}
"""

def roomFile = """\
RoomModel test {
	ActorClass ATest {
	}
}
"""

GradleProjectBuilder.build("etriceCSnapshotTest") {
	write("build.gradle", buildFile)
	write("model/test.room", roomFile)
	gradle("build") {
		assert task(":generateRoom")?.outcome == TaskOutcome.SUCCESS
	}
	// Basic snapshot assertions: check deterministic key tokens in generated file
	assert exists("build/src-gen/room/test/ATest.c")
	def gen = new File(projectDir.toFile(), "build/src-gen/room/test/ATest.c").text
	assert gen.contains("ATest")
	assert gen.contains("#include")
}
}

@Test
void "model zip is not run on regular assemble (regression guard for #4)"() {
def buildFile = """\
plugins {
	id 'de.protos.etrice-base'
}
"""

GradleProjectBuilder.build("etriceArchivesModelGuardTest") {
	write("build.gradle", buildFile)
	gradle("assemble") {
		assert task(":zipModel") == null : "modelZip must not run on regular assemble"
	}
}}

@Test
void "source zip is not run on regular assemble (regression guard for #4)"() {
// Ensure that the source-publish plugin also does not attach to archives
// even when a source zip task is present

def buildFile = """\
plugins {
	id 'base'
	id 'de.protos.source-publish'
}
zipSource.from 'src'
"""

GradleProjectBuilder.build("etriceArchivesSourceGuardTest") {
	write("build.gradle", buildFile)
	write("src/dummy.c", "int x() { return 1; }")
	gradle("assemble") {
		assert task(":zipSource") == null : "zipSource task must not run on regular assemble"
	}
}}

}
