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
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.hibernate.FetchMode

/**
 * Campaign Services.
 */
class CrmCampaignService {

    def crmContentService
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
        CrmCampaignStatus.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted $count campaigns in tenant $tenant")
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List of CrmCampaign domain instances
     */
    def list(Map params = [:]) {
        listCampaigns([:], params)
    }

    /**
     * Find CrmCampaign instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List of CrmCampaign domain instances
     */
    def list(Map query, Map params) {
        listCampaigns(query, params)
    }

    /**
     * Find CrmCampaign instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List of CrmCampaign domain instances
     */
    def listCampaigns(Map query, Map params) {
        CrmCampaign.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (query.number) {
                or {
                    ilike('number', SearchUtils.wildcard(query.number))
                    ilike('code', SearchUtils.wildcard(query.number))
                }
            }
            if (query.name) {
                ilike('name', SearchUtils.wildcard(query.name))
            }
            if (query.status) {
                status {
                    or {
                        ilike('name', SearchUtils.wildcard(query.status))
                        eq('param', query.status)
                    }
                }
            }
        }
    }

    CrmCampaign createCampaign(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = CrmCampaign.findByNumberAndTenantId(params.number, tenant)
        if (!m) {
            m = new CrmCampaign()
            def args = [m, params, [include: CrmCampaign.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (!m.status) {
                m.status = CrmCampaignStatus.findAllByTenantId(tenant, [sort: 'orderIndex', order: 'asc']).find { it }
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

    CrmCampaign getCampaign(Long id) {
        CrmCampaign.get(id)
    }

    CrmCampaign findByNumber(String number) {
        CrmCampaign.findByNumberAndTenantId(number, TenantUtils.tenant)
    }

    CrmCampaign findByCode(String campaignCode, String handler = null) {
        // First try to find a campaign with the exact number or code.
        def campaign = CrmCampaign.createCriteria().get() {
            eq('tenantId', TenantUtils.tenant)
            or {
                eq('number', campaignCode)
                eq('code', campaignCode)
            }
            if (handler) {
                eq('handlerName', handler)
            }
            fetchMode('status', FetchMode.JOIN)
            order 'dateCreated', 'desc'
            maxResults 1
        }
        if (campaign) {
            return campaign.active ? campaign : null
        }
        // Find a campaign with code matching regular expression.
        campaignCode = campaignCode.toLowerCase()
        CrmCampaign.createCriteria().list() {
            eq('tenantId', TenantUtils.tenant)
            isNotNull('code')
            if (handler) {
                eq('handlerName', handler)
            }
            fetchMode('status', FetchMode.JOIN)
            order 'dateCreated', 'desc'
        }.find { (campaignCode =~ it.code).find() && it.active }
    }

    def getCampaignResource(CrmCampaign crmCampaign, String resourceName) {
        crmContentService.findResourcesByReference(crmCampaign, [title: '=' + resourceName]).find { it }
    }

}
