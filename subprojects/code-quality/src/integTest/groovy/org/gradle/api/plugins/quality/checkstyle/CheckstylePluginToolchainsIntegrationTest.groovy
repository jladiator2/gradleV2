/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.quality.checkstyle

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.hamcrest.Matcher
import org.junit.Assume

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.CoreMatchers.containsString

class CheckstylePluginToolchainsIntegrationTest extends AbstractIntegrationSpec {

    def "uses jdk from toolchains"() {
        given:
        writDummyConfig()
        file('src/main/java/Dummy.java') << "class Dummy {}"
        def jdk = setupExecutorForToolchains {
            it.languageVersion > Jvm.current().javaVersion
        }
        writeBuildFile(jdk)

        when:
        def result = executer.withArgument("--info").withTasks("checkstyleMain").run()

        then:
        result.output.contains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "should not use toolchains if toolchain JDK matches current running JDK"() {
        given:
        writDummyConfig()
        file('src/main/java/Dummy.java') << "class Dummy {}"
        def jdk = setupExecutorForToolchains {
            it.javaHome.toAbsolutePath().toString() == Jvm.current().javaHome.absolutePath
        }
        writeBuildFile(jdk)

        when:
        def result = executer.withArgument("--info").withTasks("checkstyleMain").run()

        then:
        !result.output.contains("Running checkstyle with toolchain")
    }

    def "analyze good code"() {
        goodCode()
        def jdk = setupExecutorForToolchains {
            it.languageVersion > Jvm.current().javaVersion
        }
        writeBuildFile(jdk)
        writeConfigFileWithTypeName()

        expect:
        succeeds('checkstyleMain')
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class2"))
    }

    def "analyze bad code"() {
        executer.withDefaultLocale(new Locale('en'))
        badCode()
        def jdk = setupExecutorForToolchains {
            it.languageVersion > Jvm.current().javaVersion
        }
        writeBuildFile(jdk)
        writeConfigFileWithTypeName()

        expect:
        fails("checkstyleMain")
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertHasErrorOutput("Name 'class1' must match pattern")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    Jvm setupExecutorForToolchains(Spec<? super JvmInstallationMetadata> jvmFilter) {
        Jvm jdk = AvailableJavaHomes.getAvailableJdk(jvmFilter)
        Assume.assumeNotNull(jdk)
        executer.beforeExecute {
            withArguments("-Porg.gradle.java.installations.paths=${jdk.javaHome.absolutePath}", "--info")
        }
        return jdk
    }

    private void writeBuildFile(Jvm jvm) {
        buildFile << """
    plugins {
        id 'groovy'
        id 'java'
        id 'checkstyle'
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation localGroovy()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
        }
    }
"""
    }

    private void writDummyConfig() {
        file('config/checkstyle/checkstyle.xml') << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
</module>
        """
    }

    private void writeConfigFileWithTypeName() {
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="SuppressionFilter">
        <property name="file" value="\${config_loc}/suppressions.xml"/>
    </module>
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """

        file("config/checkstyle/suppressions.xml") << """
<!DOCTYPE suppressions PUBLIC
    "-//Puppy Crawl//DTD Suppressions 1.1//EN"
    "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd">

<suppressions>
    <suppress checks="TypeName"
          files="bad_name.java"/>
</suppressions>
        """
    }

    private void goodCode() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
    }

    private void badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/main/groovy/org/gradle/class2.java") << "package org.gradle; class class2 { }"
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

}
