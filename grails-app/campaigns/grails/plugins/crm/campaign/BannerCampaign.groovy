package grails.plugins.crm.campaign

import grails.util.ClosureToMapPopulator
import grails.util.GrailsNameUtils

/**
 * Banner image with link.
 */
class BannerCampaign {

    public static final List<String> PARAMS = ['url']

    void configure(CrmCampaign campaign, Closure arg) {
        configure(campaign, new ClosureToMapPopulator().populate(arg))
    }

    void configure(CrmCampaign campaign, Map params) {
        campaign.handlerName = GrailsNameUtils.getPropertyName(getClass())

        def cfg = params.subMap(PARAMS)

        // prefix with http:// if user forgot it.
        if(cfg.url && !cfg.url.contains(':')) {
            cfg.url = "http://${cfg.url}"
        }
        
        campaign.configuration = cfg
    }

    def process(data) {
        def reply
        // TODO process campaign...
        reply
    }
}
