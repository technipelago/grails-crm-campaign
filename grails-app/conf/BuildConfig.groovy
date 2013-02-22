grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.repos.default = "crm"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn"
    repositories {
        grailsHome()
        mavenRepo "http://labs.technipelago.se/repo/plugins-releases-local/"
        mavenRepo "http://labs.technipelago.se/repo/crm-releases-local/"
        grailsCentral()
    }
    dependencies {
    }

    plugins {
        runtime ":hibernate:$grailsVersion"
        build(":tomcat:$grailsVersion",
              ":release:2.2.0",
              ":rest-client-builder:1.0.3") {
            export = false
        }
        test(":spock:0.7") { export = false }
        test(":codenarc:0.17") { export = false }

        compile "grails.crm:crm-core:latest.integration"
        compile ":sequence-generator:latest.integration"
        runtime "grails.crm:crm-security:latest.integration"
        runtime "grails.crm:crm-ui-bootstrap:latest.integration"
        runtime "grails.crm:crm-tags:latest.integration"

        runtime ":decorator:latest.integration"
        runtime ":user-tag:latest.integration"
        compile ":text-template:latest.integration"
        compile ":selection:latest.integration"
        runtime ":selection-repository:latest.integration"
    }
}