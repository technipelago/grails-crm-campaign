import grails.plugins.crm.campaign.CampaignArtefactHandler
import grails.plugins.crm.campaign.GrailsCampaignClass
import grails.spring.BeanBuilder

class CrmCampaignGrailsPlugin {
    // Dependency group
    def groupId = "grails.crm"
    // the plugin version
    def version = "1.0.1-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]
    def watchedResources = [
            "file:./grails-app/campaigns/**/*Campaign.groovy",
            "file:./plugins/*/grails-app/campaigns/**/*Campaign.groovy"
    ]
    def artefacts = [new CampaignArtefactHandler()]

    def title = "Grails CRM Campaign Plugin" // Headline display name of the plugin
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Campaign Management for Grails CRM
'''

    def documentation = "http://grails.org/plugin/crm-campaign"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]

//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        // Configure campaign handlers
        def campaignClasses = application.campaignClasses
        campaignClasses.each { campaignClass ->
            "${campaignClass.propertyName}"(campaignClass.clazz) { bean ->
                bean.autowire = "byName"
                //bean.scope = "prototype"
            }
        }
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        println "Installed campaign handlers ${application.campaignClasses*.propertyName}"
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
        if (application.isCampaignClass(event.source)) {
            log.debug "Campaign ${event.source} modified!"

            def context = event.ctx
            if (!context) {
                log.debug("Application context not found - can't reload.")
                return
            }

            // Make sure the new selection class is registered.
            def campaignClass = application.addArtefact(GrailsCampaignClass.TYPE, event.source)

            // Create the selection bean.
            def bb = new BeanBuilder()
            bb.beans {
                "${campaignClass.propertyName}"(campaignClass.clazz) { bean ->
                    bean.autowire = "byName"
                    //bean.scope = "prototype"
                }
            }
            bb.registerBeans(context)
        }
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
