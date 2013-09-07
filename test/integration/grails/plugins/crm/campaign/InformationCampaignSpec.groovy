package grails.plugins.crm.campaign

import grails.plugin.spock.IntegrationSpec

/**
 * Tests for InformationCampaign.
 */
class InformationCampaignSpec extends IntegrationSpec {

    def informationCampaign

    def "test configuration"() {
        given: "create a fresh campaign"
        def campaign = new CrmCampaign(name: "test")

        when: "configure the campaign using productDiscountCampaign"
        informationCampaign.configure(campaign) {}

        then: "check that handleName was set, but configuration is empty"
        campaign.handlerName == 'informationCampaign'
        campaign.handlerConfig == null
        !campaign.configuration
    }
}
