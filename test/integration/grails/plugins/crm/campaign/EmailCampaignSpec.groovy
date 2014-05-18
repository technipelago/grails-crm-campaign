package grails.plugins.crm.campaign

import com.icegreen.greenmail.util.GreenMailUtil
import grails.plugin.spock.IntegrationSpec
import spock.lang.Shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    def emailCampaign
    def mailService
    def greenMail
    def grailsEventsRegistry

    @Shared
            active

    def setup() {
        if (!active) {
            active = crmCampaignService.createCampaignStatus(name: 'Active', true)
        }
    }

    def cleanup() {
        greenMail.deleteAllMessages()
    }

    def "create recipients"() {
        given:
        def latch = new CountDownLatch(1)

        grailsEventsRegistry.on("crmCampaign", "sendMail") { data ->
            def recipient = CrmCampaignRecipient.get(data.id)
            def crmCampaign = recipient.campaign
            Map cfg = crmCampaign.configuration
            String senderEmail = cfg.sender
            String senderName = cfg.senderName
            String sender = senderName ? "$senderName <${senderEmail}>".toString() : senderEmail
            String htmlBody = crmEmailCampaignService.render(crmCampaign, recipient)

            mailService.sendMail {
                to data.email
                from sender
                subject cfg.subject
                body htmlBody
            }
            latch.countDown()
        }

        when:
        def campaign = crmCampaignService.createCampaign(name: "Test", status: active, true)
        emailCampaign.configure(campaign) {
            subject = "Integration test"
            sender = "goran@technipelago.se"
            parts = ['html', 'text']
            html = """<h1>Hello Räksmörgås!</h1>"""
            text = """Hello Räksmörgås!"""
        }

        then:
        !campaign.hasErrors()

        when: "Add 2 unique recipients"
        def count = crmEmailCampaignService.createRecipients(campaign,
                ['goran@technipelago.se', 'goran@avtala.se', 'goran@technipelago.se'])

        then:
        count == 2
        CrmCampaignRecipient.countByCampaign(campaign) == 2
        CrmCampaignRecipient.countByCampaignAndDateSentIsNull(campaign) == 2

        when: "send mail queue"
        crmEmailCampaignService.send()
        latch.await(5L, TimeUnit.SECONDS)

        then:
        CrmCampaignRecipient.countByCampaignAndDateSentIsNull(campaign) == 0
        greenMail.getReceivedMessages().length == 2

        when:
        def message = greenMail.getReceivedMessages()[0]

        then:
        message.subject == "Integration test"
        ["goran@technipelago.se", "goran@avtala.se"].contains(GreenMailUtil.getAddressList(message.from))
    }
}
