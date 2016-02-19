package grails.plugins.crm.campaign

import grails.util.ClosureToMapPopulator
import grails.util.GrailsNameUtils

/**
 * Email Marketing Campaign.
 */
class EmailCampaign {

    private static final List<String> KNOWN_PROPERTIES =
            ['sender', 'senderName', 'replyTo', 'cc', 'bcc', 'subject', 'template', 'external']

    def crmEmailCampaignService

    void configure(CrmCampaign campaign, Closure arg) {
        configure(campaign, new ClosureToMapPopulator().populate(arg))
    }

    void configure(CrmCampaign campaign, Map params) {
        campaign.handlerName = GrailsNameUtils.getPropertyName(getClass())
        campaign.configuration = params.subMap(KNOWN_PROPERTIES)
        StringBuilder allParts = new StringBuilder()
        params.findAll { !KNOWN_PROPERTIES.contains(it.key) }.each { key, value ->
            crmEmailCampaignService.setPart(campaign, key, value?.toString())
            if(value) {
                allParts << value.toString()
            }
        }

        if (!params.preview) {
            // Scan hyperlinks in all parts and add CrmCampaignTrackable for each link found.
            try {
                crmEmailCampaignService.collectHyperlinks(campaign, allParts.toString())
            } catch (Exception e) {
                log.error "Failed to scan hyperlinks in campaign [$campaign]", e
            }
        }
    }

    // TODO this method is not tested and not used.
    def process(data) {
        def recipient = CrmCampaignRecipient.get(data.id)
        if (recipient) {
            return crmEmailCampaignService.render(recipient.campaign, recipient, data)
        }
        return null
    }

}
