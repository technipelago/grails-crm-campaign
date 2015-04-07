package grails.plugins.crm.campaign

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import grails.test.spock.IntegrationSpec
import test.TestEntity

import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test spec for CrmEmailCampaign.
 */
class EmailCampaignSpec extends IntegrationSpec {

    def crmCampaignService
    def crmEmailCampaignService
    def crmEmailBounceService
    def emailCampaign
    def mailService
    def greenMail
    def grailsEventsRegistry
    def sessionFactory

    def cleanup() {
        greenMail.deleteAllMessages()
    }

    def "create recipients"() {
        given:
        def active = crmCampaignService.createCampaignStatus(name: 'Active', true)
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
            sender = "me@mycompany.com"
            parts = ['html', 'text']
            html = """<h1>Hello Räksmörgås!</h1>"""
            text = """Hello Räksmörgås!"""
        }

        then:
        !campaign.hasErrors()

        when: "Add 2 unique recipients"
        def count = crmEmailCampaignService.createRecipients(campaign,
                ['me@mycompany.com', 'foo@bar.com', 'me@mycompany.com'])

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
        ["me@mycompany.com", "foo@bar.com"].contains(GreenMailUtil.getAddressList(message.from))
    }

    def "bounce tracking"() {
        given:
        def person = new TestEntity(name: "Joe Spammer", postalCode: "12345", city: "Spam City", age: 42).save(failOnError: true)
        def active = crmCampaignService.createCampaignStatus(name: 'Active', true)
        def campaign = crmCampaignService.createCampaign(name: "Test", status: active, true)
        def fakeRecipient = new CrmCampaignRecipient(campaign: campaign, ref: 'testEntity@' + person.id, email: 'problem@foo.com', dateSent: new Date()).save(failOnError: true)
        def mailServer = new GreenMail(ServerSetupTest.IMAP);
        mailServer.start();

        when:
        GreenMailUser user = mailServer.setUser('me@mycompany.com', 'me', 'secret');

        // create an e-mail message using javax.mail ..
        MimeMessage message = new MimeMessage((Session) null)
        message.setFrom(new InternetAddress('mailer-daemon@foo.com'))
        message.addRecipient(Message.RecipientType.TO, new InternetAddress('me@mycompany.com'))
        message.setSubject('Undelivered Mail Returned to Sender')
        message.setText("""This is the mail system at host smtp-relay.foo.com.

I'm sorry to have to inform you that your message could not
be delivered to one or more recipients. It's attached below.

For further assistance, please send mail to postmaster.

If you do so, please include this problem report. You can
delete your own text from the attached returned message.

                  The mail system

<problem@foo.com>: host
  spamhaus.foo.com[0.0.0.0] said: 550 5.7.1 Service
  unavailable; Client host [127.0.0.1] blocked using Spamhaus; To request
  removal from this list see http://www.spamhaus.org/lookup.lasso (in reply
  to RCPT TO command)
...
""");

        user.deliver(message) // use greenmail to store the message

        crmEmailBounceService.scan('localhost', ServerSetupTest.IMAP.getPort(), 'me', 'secret')
        sessionFactory.getCurrentSession().flush()
        fakeRecipient.refresh()

        then:
        fakeRecipient.dateBounced != null
        fakeRecipient.reason != null
        person.getTagValue() == ['bounced']
    }
}
