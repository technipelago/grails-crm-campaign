/*
 * Copyright (c) 2013 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import grails.plugins.crm.campaign.CampaignArtefactHandler
import grails.plugins.crm.campaign.GrailsCampaignClass
import grails.spring.BeanBuilder

class CrmCampaignGrailsPlugin {
    def groupId = "grails.crm"
    def version = "1.2.2"
    def grailsVersion = "2.2 > *"
    def dependsOn = [:]
    def loadAfter = ['crmTags']
    def pluginExcludes = [
            "grails-app/domain/test/**",
            "grails-app/views/error.gsp"
    ]
    def watchedResources = [
            "file:./grails-app/campaigns/**/*Campaign.groovy",
            "file:./plugins/*/grails-app/campaigns/**/*Campaign.groovy"
    ]
    def artefacts = [new CampaignArtefactHandler()]

    def title = "GR8 CRM Campaign Plugin"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Campaign Management for GR8 CRM
'''
    def documentation = "https://github.com/technipelago/grails-crm-campaign"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-campaign/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-campaign"]

    def doWithSpring = {
        // Configure campaign handlers
        def campaignClasses = application.campaignClasses
        campaignClasses.each { campaignClass ->
            "${campaignClass.propertyName}"(campaignClass.clazz) { bean ->
                bean.autowire = "byName"
            }
        }
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

            // Create the campaign bean.
            def bb = new BeanBuilder()
            bb.beans {
                "${campaignClass.propertyName}"(campaignClass.clazz) { bean ->
                    bean.autowire = "byName"
                }
            }
            bb.registerBeans(context)
        }
    }
}
