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

class CrmCampaignFilters {

    def grailsApplication

    def filters = {
        def campaignLandingPages = grailsApplication.config.crm.campaign.landingPages
        if (campaignLandingPages) {
            log.info "Setting up filters for campaign landing pages $campaignLandingPages"
            def parameters = grailsApplication.config.crm.campaign.landingParam ?: 'campaign'
            if (!(parameters instanceof Collection)) {
                parameters = [parameters]
            }
            def sessionParam = grailsApplication.config.crm.campaign.sessionParam ?: 'campaign'
            campaignLandingPages.each { c, a ->
                "$c"(controller: c, action: a) {
                    before = {
                        if (request.method == 'GET') {
                            def campaign = parameters.inject(null) { p, v -> p ?: params[v] }
                            if (campaign) {
                                request.session.setAttribute(sessionParam, campaign)
                            }
                            if (log.isDebugEnabled()) {
                                log.debug "$controllerName:${actionName ?: 'index'} is a campaign landing page and campaign=$campaign"
                            }
                        }
                        true
                    }
                }
            }
        }
    }
}