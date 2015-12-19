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
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import org.apache.commons.lang.StringUtils
import org.grails.databinding.SimpleMapDataBindingSource
import org.hibernate.FetchMode
import grails.plugins.selection.Selectable
import org.springframework.web.multipart.MultipartFile

/**
 * Campaign Services.
 */
class CrmCampaignService {

    def grailsApplication
    def grailsWebDataBinder
    def crmSecurityService
    def crmContentService
    def crmTagService
    def sequenceGeneratorService
    def messageSource

    @Listener(namespace = "crmCampaign", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        def tenant = crmSecurityService.getTenantInfo(event.tenant)
        def locale = tenant.locale
        TenantUtils.withTenant(tenant.id) {
            crmTagService.createTag(name: CrmCampaign.name, multiple: true)
            sequenceGeneratorService.initSequence(CrmCampaign, null, tenant.id, 1, "%s")
            createCampaignStatus(orderIndex: 1, param: 'pending',
                    name: getLocalizedName('crmCampaignStatus.name.pending', 'Pending', locale),).save(failOnError: true)
            createCampaignStatus(orderIndex: 2, param: 'active',
                    name: getLocalizedName('crmCampaignStatus.name.active', 'Active', locale),).save(failOnError: true)
            createCampaignStatus(orderIndex: 9, param: 'closed',
                    name: getLocalizedName('crmCampaignStatus.name.closed', 'Closed', locale),).save(failOnError: true)
        }
    }

    private String getLocalizedName(String code, String fallback, Locale locale) {
        messageSource.getMessage(code, null, fallback, locale)
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
        for (c in CrmCampaign.findAllByTenantId(tenant)) {
            deleteCampaign(c)
        }
        // Remove types and statuses.
        CrmCampaignStatus.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted $count campaigns in tenant $tenant")
    }

    List<String> getEnabledCampaignHandlers() {
        def enabledHandlers = []
        def campaignClasses = grailsApplication.campaignClasses
        def config = grailsApplication.config.crm.campaign
        campaignClasses.each { campaignClass ->
            def campaignHandler = campaignClass.propertyName
            def enabled = config."$campaignHandler".enabled
            if (enabled != false) {
                enabledHandlers << campaignHandler
            }
        }
        enabledHandlers
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List of CrmCampaign domain instances
     */
    @Selectable
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
    @Selectable
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
        def tagged
        if (query.tags) {
            tagged = crmTagService.findAllIdByTag(CrmCampaign, query.tags) ?: [0L]
        }

        CrmCampaign.createCriteria().list(params) {

            eq('tenantId', TenantUtils.tenant)
            if (tagged) {
                inList('id', tagged)
            }

            if (query.handlerName) {
                eq('handlerName', query.handlerName)
            }

            if (query.parent) {
                parent {
                    ilike('number', SearchUtils.wildcard(query.parent))
                }
            }

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

            if (query.fromDate && query.toDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d1 = query.fromDate instanceof Date ? query.fromDate : DateUtils.parseDate(query.fromDate, timezone)
                def d2 = query.toDate instanceof Date ? query.toDate : DateUtils.parseDate(query.toDate, timezone)
                or {
                    between('startTime', d1, d2)
                    between('endTime', d1, d2)
                }
            } else if (query.fromDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d1 = DateUtils.parseDate(query.fromDate, timezone)
                or {
                    ge('startTime', d1)
                    gt('endTime', d1)
                }
            } else if (query.toDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d2 = DateUtils.parseDate(query.toDate, timezone)
                or {
                    lt('startTime', d2)
                    le('endTime', d2)
                }
            }
        }
    }

    CrmCampaign createCampaign(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = CrmCampaign.findByNumberAndTenantId(params.number, tenant)
        if (!m) {
            m = new CrmCampaign()
            grailsWebDataBinder.bind(m, params as SimpleMapDataBindingSource, null, CrmCampaign.BIND_WHITELIST, null, null)
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

    CrmCampaign copyCampaign(CrmCampaign templateCampaign, boolean save = false) {
        def tenant = TenantUtils.tenant
        def props = ['handlerName', 'handlerConfig', 'startTime', 'endTime', 'status', 'parent'] +
                CrmCampaign.BIND_WHITELIST
        // Create a new campaign instance.
        def crmCampaign = new CrmCampaign(tenantId: tenant)
        // Copy domain properties
        for (p in props) {
            crmCampaign[p] = templateCampaign[p]
        }
        // If the source campaign was assigned to a user, assign the new campaign to the current user.
        if (templateCampaign.username) {
            def user = crmSecurityService.getCurrentUser()
            crmCampaign.username = user?.username
        }

        // Number must be unique so we cannot copy it.
        crmCampaign.number = null

        // NOTE! child campaigns are not copied.

        // Copy target
        for (t in templateCampaign.target) {
            def target = new CrmCampaignTarget(campaign: crmCampaign)
            target.orderIndex = t.orderIndex
            target.operation = t.operation
            target.name = t.name
            target.description = t.description
            target.uriString = t.uriString

            if (target.validate()) {
                crmCampaign.addToTarget(target)
            }
        }

        if (save) {
            crmCampaign.save()
        } else {
            crmCampaign.validate()
            crmCampaign.clearErrors()
        }
        return crmCampaign
    }

    CrmCampaignStatus createCampaignStatus(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmCampaignStatus.findByParamAndTenantId(params.param, tenant)
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
        CrmCampaign.findByIdAndTenantId(id, TenantUtils.tenant, [cache: true])
    }

    CrmCampaign findByNumber(String number) {
        CrmCampaign.findByNumberAndTenantId(number, TenantUtils.tenant, [cache: true])
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
            cache true
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
            cache true
        }.find { (campaignCode =~ it.code).find() && it.active }
    }

    String deleteCampaign(CrmCampaign crmCampaign) {
        def tenant = crmCampaign.tenantId
        def user = crmSecurityService.getCurrentUser()
        def eventPayload = [tenant: tenant, id: crmCampaign.id, user: user.username]
        def resources = crmContentService.findResourcesByReference(crmCampaign)
        for (r in resources) {
            crmContentService.deleteReference(r)
        }
        def tombstone = crmCampaign.toString()
        crmCampaign.delete(flush: true)
        log.debug "Deleted campaign [$tombstone] in tenant [$tenant]"
        event(for: 'crmCampaign', topic: 'deleted', data: eventPayload)
        return tombstone
    }

    def addCampaignResource(CrmCampaign campaign, InputStream is, String filename, Long length, String mimeType, Map params = [:]) {
        if (!params.status) {
            params.status = 'shared'
        }
        crmContentService.createResource(is, filename, length, mimeType, campaign, params)
    }

    def addCampaignResource(CrmCampaign campaign, MultipartFile file, Map params = [:]) {
        if (!params.status) {
            params.status = 'shared'
        }
        crmContentService.createResource(file.inputStream, file.originalFilename, file.size, file.contentType, campaign, params)
    }

    def addCampaignResource(CrmCampaign campaign, String text, String filename, Map params = [:]) {
        if (!params.status) {
            params.status = 'shared'
        }
        crmContentService.createResource(text, filename, campaign, params)
    }

    def getCampaignResource(CrmCampaign crmCampaign, String resourceName) {
        crmContentService.findResourcesByReference(crmCampaign, [name: '=' + resourceName]).find { it }
    }

}
