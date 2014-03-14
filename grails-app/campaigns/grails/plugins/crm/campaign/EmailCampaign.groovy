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
        def cfg = params.subMap(['sender', 'senderName', 'subject', 'parts', 'template', 'external'])
        def s = new StringBuilder()
        def parts = cfg.parts
        if(! (parts instanceof Collection)) {
            parts = [parts]
        }
        for (part in parts) {
            def p = params[part]
            cfg[part] = p
            if (p) {
                s << p
            }
        }

        if(! params.preview) {
            // Scan hyperlinks in all parts and add CrmCampaignTrackable for each link found.
            try {
                crmEmailCampaignService.collectHyperlinks(campaign, s.toString())
            } catch (Exception e) {
                log.error "Failed to scan hyperlinks in campaign [$campaign]", e
            }
        }

        campaign.configuration = cfg
    }

    def process(data) {
        def recipient = CrmCampaignRecipient.get(data.id)
        def reply = crmEmailCampaignService.render(recipient, [])
        // TODO this is not tested and not used.
        return reply
    }

}
