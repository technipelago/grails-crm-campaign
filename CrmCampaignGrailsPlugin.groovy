/*
 * Copyright (c) 2015 Goran Ehrsson.
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
    def groupId = ""
    def version = "2.4.3-SNAPSHOT"
    def grailsVersion = "2.4 > *"
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

    def title = "GR8 CRM Campaign Services"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
This plugin provide storage and services for managing campaigns in GR8 CRM based applications.
A campaign is something that has a message and a target group,
for example an email campaign, a product discount or a web site banner.
Custom plugins can provide other campaign types with Grails artifacts.
'''
    def documentation = "http://gr8crm.github.io/plugins/crm-campaign/"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-campaign/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-campaign"]

    def features = {
        crmCampaign {
            description "Campaign Management"
            link controller: "crmCampaign", action: "index"
            permissions {
                guest "crmCampaign:index,list,show"
                partner "crmCampaign:index,list,show"
                user "crmCampaign:*"
                admin "crmCampaign:*", "productDiscountCampaign,informationCampaign:edit"
            }
        }
    }

    def doWithSpring = {
        // Configure campaign handlers
        def campaignClasses = application.campaignClasses
        campaignClasses.each { campaignClass ->
            def campaignHandler = campaignClass.propertyName
            def enabled  = application.config.crm.campaign."${campaignHandler}".enabled
            if(enabled != false) {
                "${campaignHandler}"(campaignClass.clazz) { bean ->
                    bean.autowire = "byName"
                }
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
        def enabledHandlers = []
        def campaignClasses = application.campaignClasses
        campaignClasses.each { campaignClass ->
            def campaignHandler = campaignClass.propertyName
            def enabled  = application.config.crm.campaign."${campaignHandler}".enabled
            if(enabled != false) {
                enabledHandlers << campaignHandler
            }
        }
        println "Installed campaign handlers $enabledHandlers"
    }

    def onChange = { event ->
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
