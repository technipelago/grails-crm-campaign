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

package grails.plugins.crm.campaign

import grails.events.Listener
import grails.plugins.crm.core.TenantUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

/**
 * Campaign Services.
 */
class CrmCampaignService {

    def textTemplateService
    def crmTagService
    def sequenceGeneratorService

    @Listener(namespace = "crmCampaign", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        def tenant = event.tenant
        TenantUtils.withTenant(tenant) {
            crmTagService.createTag(name: CrmCampaign.name, multiple: true)
            sequenceGeneratorService.initSequence(CrmCampaign, null, tenant, 1, "%s")
            createCampaignStatus(orderIndex: 1, name: "Planerad", param: "pending").save(failOnError: true)
            createCampaignStatus(orderIndex: 2, name: "Aktiv", param: "active").save(failOnError: true)
            createCampaignStatus(orderIndex: 9, name: "Avslutad", param: "closed").save(failOnError: true)
        }
    }

    @Listener(namespace = "crmTenant", topic = "requestDelete")
    def requestDeleteTenant(event) {
        def tenant = event.id
        def count = 0
        count += CrmCampaign.countByTenantId(tenant)
        count += CrmCampaignType.countByTenantId(tenant)
        count += CrmCampaignStatus.countByTenantId(tenant)
        count ? [namespace: 'crmCampaign', topic: 'deleteTenant'] : null
    }

    @Listener(namespace = "crmCampaign", topic = "deleteTenant")
    def deleteTenant(event) {
        def tenant = event.id
        def count = CrmCampaign.countByTenantId(tenant)
        // Disconnect all parent/child associations.
        CrmCampaign.findAllByTenantIdAndParentIsNotNull(tenant).each { it.parent = null; it.save() }
        // Remove all campaigns
        CrmCampaign.findAllByTenantId(tenant)*.delete()
        // Remove types and statuses.
        CrmCampaignType.findAllByTenantId(tenant)*.delete()
        CrmCampaignStatus.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted $count campaigns in tenant $tenant")
    }

    List<String> getCampaignTypes() {
        ['productDiscountCampaign']
    }

    CrmCampaign getCampaign(String number) {
        CrmCampaign.findByNumberAndTenantId(number, TenantUtils.tenant)
    }

    CrmCampaign createCampaign(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = CrmCampaign.findByNumberAndTenantId(params.number, tenant)
        if (!m) {
            m = new CrmCampaign()
            def args = [m, params, [include: CrmCampaign.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (! m.status) {
                m.status = CrmCampaignStatus.findAllByTenantId(tenant, [sort: 'orderIndex', order: 'asc']).find{it}
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    CrmCampaignStatus createCampaignStatus(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = CrmCampaignStatus.findByNameAndTenantId(params.name, tenant)
        if (!m) {
            m = new CrmCampaignStatus(params)
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    String content(String templateName, String contentType = null, String language = null) {
        textTemplateService.content(templateName, contentType, language)
    }

    CrmCampaign findByCode(String campaignCode, String handler = null) {
        def result = CrmCampaign.createCriteria().list() {
            eq('tenantId', TenantUtils.tenant)
            isNotEmpty('codes')
            if(handler) {
                eq('handler', handler)
            }
        }
        for (c in result) {
            if (c.codes.find {
                if(it[0] == '~') {
                    def regex = it.substring(1)
                    return (campaignCode.toLowerCase() =~ regex).find()
                } else {
                    return campaignCode.equalsIgnoreCase(it)
                }
            }) {
                return c
            }
        }
        return null
    }
}
