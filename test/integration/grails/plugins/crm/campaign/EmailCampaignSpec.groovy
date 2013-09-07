package grails.plugins.crm.campaign

import grails.plugin.spock.IntegrationSpec
import grails.plugins.crm.core.TenantUtils
import spock.lang.Shared

/**
 * Created with IntelliJ IDEA.
 * User: goran
 * Date: 2013-08-28
 * Time: 23:10
 * To change this template use File | Settings | File Templates.
 */
class EmailCampaignSpec extends IntegrationSpec {

    def crmCampaignService
    def crmEmailCampaignService

    @Shared active

    def setup() {
        if (!active) {
            active = crmCampaignService.createCampaignStatus(name: 'Active', true)
        }
    }

    def "create recipients"() {
        when:
        def campaign = crmCampaignService.createCampaign(name: "Test", status: active, true)
        def is = new ByteArrayInputStream("<h1>Hello Räksmörgås!</h1>".getBytes("UTF-8"))
        crmEmailCampaignService.setEmailBody(campaign, is, 'text/html', [username: "test"])
        is.close()

        then:
        !campaign.hasErrors()

        when:
        crmEmailCampaignService.createRecipients(campaign, ['goran@technipelago.se', 'goran@avtala.se'])

        then:
        CrmCampaignRecipient.countByCampaign(campaign) == 2
        CrmCampaignRecipient.countByCampaignAndDateSentIsNull(campaign) == 2

        when:
        crmEmailCampaignService.send()

        then:
        CrmCampaignRecipient.countByCampaignAndDateSentIsNull(campaign) == 0
    }
}
