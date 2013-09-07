package grails.plugins.crm.campaign

import grails.util.ClosureToMapPopulator
import grails.util.GrailsNameUtils

/**
 * Banner image with link.
 */
class BannerCampaign {

    void configure(CrmCampaign campaign, Closure arg) {
        configure(campaign, new ClosureToMapPopulator().populate(arg))
    }

    void configure(CrmCampaign campaign, Map params) {
        campaign.handlerName = GrailsNameUtils.getPropertyName(getClass())
        campaign.configuration = params.subMap(['url'])
    }

    def process(data) {
        def reply
        // TODO process campaign...
        reply
    }
}
