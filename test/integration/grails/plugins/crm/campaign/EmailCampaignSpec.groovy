package grails.plugins.crm.campaign

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import grails.test.spock.IntegrationSpec
import spock.lang.Ignore
import test.TestEntity

import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test spec for CrmEmailCampaign.
 */
class EmailCampaignSpec extends IntegrationSpec {

    def grailsApplication
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

    def "beacon image is correct size"() {
        expect:
        crmEmailCampaignService.getBeaconImage().length == 141
    }

    def "create email layout"() {
        given:
        def campaign = crmCampaignService.createCampaign(name: "Test layout", true)

        when:
        emailCampaign.configure(campaign) {
            subject = "Layout Test"
            sender = "me@mycompany.com"
            senderName = "Integration Test"
            body = """<h1>Hello \${name}!</h1><p>Did the test pass?</p>"""
        }

        then:
        !campaign.hasErrors()
        crmCampaignService.getCampaignResource(campaign, 'body.html')
    }

    def "collect hyperlinks in email template"() {
        when:
        def campaign = crmCampaignService.createCampaign(name: "Links layout", true)

        then:
        !campaign.trackables

        when:
        crmEmailCampaignService.collectHyperlinks(campaign, """<body>
                <h1>Link Test</h1>
                <p><a href="https://grails.org/">Grails</a><br/>
                <a href="http://start.spring.io/"></a></p>
                <div><a href="http://www.technipelago.se/development.html"></a></div>
                </body>""")
        then:
        campaign.trackables.size() == 3
    }

    def "replace hyperlinks in email template"() {
        given:
        def campaign = crmCampaignService.createCampaign(name: "Links layout", true)
        def body = """<body>
<h1>Link Test</h1>
<p><a href="https://grails.org/">Grails</a><br/>
<a href="http://start.spring.io/"></a></p>
<div><a href="http://www.technipelago.se/development.html"></a></div>
</body>"""
        when:
        crmEmailCampaignService.collectHyperlinks(campaign, body)

        then:
        campaign.trackables.size() == 3

        when:
        def recipient = new CrmCampaignRecipient(campaign: campaign, email: 'my@company.com').save(failOnError: true)
        def result = crmEmailCampaignService.replaceHyperlinks(recipient, body, [track: false])

        then:
        !result.contains('grails.org')
        !result.contains('start.spring.io')
        !result.contains('technipelago.se')
    }

    def "track recipient activity"() {
        given:
        def campaign = crmCampaignService.createCampaign(name: "Click click", true)

        when:
        def recipient = new CrmCampaignRecipient(campaign: campaign, email: 'my@company.com').save(failOnError: true)

        then:
        !recipient.dateOpened

        when:
        crmEmailCampaignService.track(recipient.guid)

        then:
        recipient.dateOpened
    }

    def "simulate click on hyperlink"() {
        given:
        def campaign = crmCampaignService.createCampaign(name: "Click me", true)
        def body = """<body>
<h1>Click Me!</h1>
<p><a href="https://grails.org/">Grails</a><br/>
<a href="http://start.spring.io/"></a></p>
<div><a href="http://www.technipelago.se/development.html"></a></div>
</body>"""
        when:
        crmEmailCampaignService.collectHyperlinks(campaign, body)
        def recipient = new CrmCampaignRecipient(campaign: campaign, email: 'my@company.com').save(failOnError: true)
        def result = crmEmailCampaignService.replaceHyperlinks(recipient, body, [track: false])

        then:
        campaign.trackables.size() == 3

        when:
        def grailsLink = campaign.trackables.find { it.href.contains('grails') }
        def href = crmEmailCampaignService.link(recipient.guid, grailsLink.guid, '127.0.0.1')

        then:
        href == 'https://grails.org/'

        when:
        href = crmEmailCampaignService.link(recipient.guid, "invalid guid", '127.0.0.1')

        then:
        href.contains('newsletter') // <baseurl>/newsletter/<campaign>/<recipient>.html

        when:
        href = crmEmailCampaignService.link(recipient.guid, null/*no guid*/, '127.0.0.1')

        then:
        href.contains('newsletter')
    }

    def "user clicks opt-out link"() {
        given:
        def latch = new CountDownLatch(1)
        def campaign = crmCampaignService.createCampaign(name: "Spam", true)
        final List tags = []
        grailsEventsRegistry.on("crmCampaign", "optout") { data ->
            tags.addAll(data.tags)
            latch.countDown()
        }

        when:
        def recipient = new CrmCampaignRecipient(campaign: campaign, email: 'my@company.com').save(failOnError: true)

        then:
        !recipient.dateOptOut
        !recipient.dateOpened

        when:
        crmEmailCampaignService.optOut(recipient, ['boring', 'frequency'])
        latch.await(5L, TimeUnit.SECONDS)
        recipient = recipient.get(recipient.id)

        then:
        recipient.dateOptOut
        recipient.dateOpened
        tags.contains('boring')
        tags.contains('frequency')
    }

    def "campaign public id"() {
        when:
        def campaign = crmCampaignService.createCampaign(name: "Public ID is constructed in a really wierd way", true)

        then:
        crmEmailCampaignService.getCampaignByPublicId(campaign.publicId)
    }

    def "assemble message from multiple parts"() {
        given:
        def campaign = crmCampaignService.createCampaign(name: "A campaign with 3 parts", true)

        when:
        crmEmailCampaignService.setPart(campaign, 'body', '<html><div>[#include "left.html"]</div><div>[#include "right.html"]</div></html>')
        crmEmailCampaignService.setPart(campaign, 'left', '<div>Left column</div>')
        crmEmailCampaignService.setPart(campaign, 'right', '<div>Right column</div>')

        then:
        crmEmailCampaignService.getPart(campaign, 'none') == null
        crmEmailCampaignService.getPart(campaign, 'body') != null
        crmEmailCampaignService.getPart(campaign, 'left') != null
        crmEmailCampaignService.getPart(campaign, 'right') != null

        when:
        def recipient = new CrmCampaignRecipient(campaign: campaign, email: 'my@company.com').save(failOnError: true)
        def result = crmEmailCampaignService.render(campaign, recipient, [test: true])

        then:
        result.contains("<body><div><div>Left column</div></div><div><div>Right column</div></div></body>")
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
            String htmlBody = crmEmailCampaignService.render(crmCampaign, recipient, [foo: 42])

            mailService.sendMail {
                to data.email
                from sender
                subject cfg.subject
                body htmlBody
            }
            latch.countDown()
        }

        when:
        def campaign = crmCampaignService.createCampaign(name: "Test", true)
        emailCampaign.configure(campaign) {
            subject = "Integration test"
            sender = "me@mycompany.com"
            body = """<h1>Hello \${foo} Räksmörgås!</h1>"""
        }

        then:
        !campaign.hasErrors()

        when: "Add 2 unique recipients"
        def count = crmCampaignService.createRecipients(campaign,
                [[email: 'me@mycompany.com'],
                 [email: 'foo@bar.com'],
                 [email: 'me@mycompany.com']
                ])

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
        GreenMailUtil.getBody(message).contains('<h1>Hello 42 R&#xe4;ksm&#xf6;rg&#xe5;s!</h1>')
    }

    @Ignore
    def "bounce tracking"() {
        given:
        def person = new TestEntity(name: "Joe Spammer", postalCode: "12345", city: "Spam City", age: 42).save(failOnError: true)
        def campaign = crmCampaignService.createCampaign(name: "Test", true)
        def fakeRecipient = new CrmCampaignRecipient(campaign: campaign, ref: 'testEntity@' + person.id, email: 'problem@foo.com', dateSent: new Date()).save(failOnError: true)
        def mailServer = new GreenMail(ServerSetupTest.IMAP)
        mailServer.start()

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
