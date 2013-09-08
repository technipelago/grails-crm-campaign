package grails.plugins.crm.campaign

import grails.util.ClosureToMapPopulator
import grails.util.GrailsNameUtils

/**
 * Email Marketing Campaign.
 */
class EmailCampaign {

    def crmEmailCampaignService

    void configure(CrmCampaign campaign, Closure arg) {
        configure(campaign, new ClosureToMapPopulator().populate(arg))
    }

    void configure(CrmCampaign campaign, Map params) {
        campaign.handlerName = GrailsNameUtils.getPropertyName(getClass())
        campaign.configuration = params.subMap(['sender', 'subject', 'html', 'text', 'template', 'external'])
        try {
            crmEmailCampaignService.collectHyperlinks(campaign, params.html)
        } catch (Exception e) {
            log.error "Failed to scan hyperlinks in campaign [$campaign]", e
        }
    }

    def process(data) {
        def reply
        // TODO process campaign...
        reply
    }
}
