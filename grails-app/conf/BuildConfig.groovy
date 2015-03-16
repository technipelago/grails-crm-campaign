grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6

grails.project.dependency.resolution = {
    inherits("global") {}
    log "warn"
    legacyResolve false
    repositories {
        grailsCentral()
        mavenCentral()
    }
    dependencies {
        test "org.spockframework:spock-grails-support:0.7-groovy-2.0"
        compile "org.ccil.cowan.tagsoup:tagsoup:1.2.1"
        compile "javax.mail:javax.mail-api:1.5.1"
        runtime "com.sun.mail:javax.mail:1.5.1"
    }

    plugins {
        build(":tomcat:$grailsVersion",
                ":release:2.2.1",
                ":rest-client-builder:1.0.3") {
            export = false
        }
        runtime ":hibernate:$grailsVersion"

        test(":spock:0.7") {
            export = false
            exclude "spock-grails-support"
        }
        test(":mail:1.0.7", ":greenmail:1.3.4") {
            export = false
        }
        test(":codenarc:0.21") { export = false }
        test(":code-coverage:1.2.7") { export = false }

        compile ":sequence-generator:1.0"
        compile ":selection:0.9.8"

        compile ":crm-core:2.0.2"
        compile ":crm-content:2.0.0"
        compile ":crm-tags:2.0.0"
    }
}
