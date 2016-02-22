package grails.plugins.crm.campaign

import grails.transaction.Transactional
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
        def partName = params._part
        if (partName) {
            def key = params.containsKey(partName) ?: 'content'
            crmEmailCampaignService.setPart(campaign, partName, params[key])
        } else {
            params.findAll { !KNOWN_PROPERTIES.contains(it.key) }.each { key, value ->
                crmEmailCampaignService.setPart(campaign, key, value?.toString())
            }
        }

        // Scan hyperlinks in all parts and add CrmCampaignTrackable for each link found.
        try {
            crmEmailCampaignService.collectHyperlinks(campaign)
        } catch (Exception e) {
            log.error "Failed to scan hyperlinks in campaign [$campaign]", e
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

    @Transactional
    def migrate(CrmCampaign crmCampaign) {
        def cfg = crmCampaign.getConfiguration()
        def parts = cfg.parts
        if(parts) {
            for(p in parts) {
                def part = cfg[p]
                if(part) {
                    crmEmailCampaignService.setPart(crmCampaign, p, part)
                    cfg[p] = null
                }
            }
            cfg.parts = null
            crmCampaign.setConfiguration(cfg)
        }
        cfg
    }
}
