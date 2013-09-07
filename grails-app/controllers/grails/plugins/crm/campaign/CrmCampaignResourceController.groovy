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

import grails.converters.JSON
import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.core.WebUtils

import javax.servlet.http.HttpServletResponse

/**
 * Public controller for access to campaign resources (images, text, html, etc.)
 */
class CrmCampaignResourceController {

    def crmContentService

    /**
     * Collect all images attached to a CrmCampaign and it's sub-campaigns.
     * @param id primary key of root CrmCampaign
     * @param number number of root CrmCampaign
     * @param code code of any CrmCampaign
     * @param t optional tenant id
     * @return render image list as JSON
     */
    def images(final Long t, final Long id, final String number, final String code) {
        if (crmContentService == null) {
            log.error "CrmCampaignResourceController#images($t, $id, $number) called from [${request.remoteAddr}], but the 'crm-content' plugin is not installed in this application!"
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            return
        }

        final Long tenant = t ?: TenantUtils.tenant
        final List<CrmCampaign> result = []
        if (id) {
            def crmCampaign = CrmCampaign.findByIdAndTenantId(id, tenant)
            if (crmCampaign) {
                result << crmCampaign
            }
        } else if (number) {
            def crmCampaign = CrmCampaign.findByNumberAndTenantId(number, tenant)
            if (crmCampaign) {
                result << crmCampaign
            }
        } else if (code) {
            result.addAll(CrmCampaign.findAllByCodeAndTenantId(code, tenant, [sort: 'number', order: 'asc']))
        }

        if (!(result || code)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        final List mediaList = []
        for (CrmCampaign crmCampaign in result) {
            collectImages(crmCampaign, mediaList)
        }
        // Default cache is public 10 minutes.
        if (params.cache == null || params.boolean("cache")) {
            WebUtils.defaultCache(response)
        } else {
            // if cache=false we cache 1 minute private.
            WebUtils.shortCache(response)
        }
        render mediaList as JSON
    }

    /**
     * Collect all shared campaign images recursively.
     * @param crmCampaign the CrmCampaign instance to start traversal at
     * @param images list to append image metadata to
     */
    private void collectImages(final CrmCampaign crmCampaign, final List images) {
        final Date now = new Date()
        if (crmCampaign.startTime != null && now < crmCampaign.startTime) {
            return // Campaign has not started yet.
        }
        if (crmCampaign.endTime != null && now > crmCampaign.endTime) {
            return // Campaign has ended.
        }
        final List result = crmContentService.findResourcesByReference(crmCampaign)
        for (r in result) {
            if (r.shared) {
                final Map md = r.metadata
                if (md.contentType.startsWith('image/')) {
                    images << [id: r.id, name: r.name, title: r.title, campaign: crmCampaign.name,
                            contentType: md.contentType, length: md.bytes, modified: md.modified,
                            uri: crm.createResourceLink(resource: r)] + crmCampaign.configuration
                }
            }
        }
        for (child in crmCampaign.children?.sort { it.number }) {
            collectImages(child, images)
        }
    }

    /**
     * Check if a filename looks like an image.
     * @param name file name to check
     * @return true if the filename ends with '.png', '.jpg' or '.gif'
     */
    private boolean isImage(final String name) {
        final String lowercaseName = name.toLowerCase()
        lowercaseName.endsWith('.png') || lowercaseName.endsWith('.jpg') || lowercaseName.endsWith('.jpeg') || lowercaseName.endsWith('.gif')
    }
}
