package grails.plugins.crm.campaign

import groovy.xml.StreamingMarkupBuilder
import org.ccil.cowan.tagsoup.Parser
import org.hibernate.Cache
import org.springframework.transaction.annotation.Transactional

import java.text.SimpleDateFormat

/**
 * Service for outbound email campaigns.
 */
class CrmEmailCampaignService {

    static transactional = false

    def grailsApplication
    def sessionFactory
    def grailsLinkGenerator

    int createRecipients(CrmCampaign campaign, List<String> recipients) {
        int count = 0
        CrmCampaign.withTransaction {
            Cache cache = sessionFactory.getCache()
            for (String email in recipients) {
                if (email && !CrmCampaignRecipient.countByCampaignAndEmail(campaign, email)) {
                    def r = new CrmCampaignRecipient(campaign: campaign, email: email).save(failOnError: true)
                    cache.evictEntity(CrmCampaignRecipient, r.ident())
                    count++
                }
            }
        }
        return count
    }

    def send(CrmCampaign campaign = null) {
        final Date now = new Date()
        final List<CrmCampaignRecipient> result = CrmCampaignRecipient.createCriteria().list([max: 250]) { // 1500 email / hour.
            isNull('dateSent')
            isNull('reason')
            delegate.campaign {
                if (campaign) {
                    eq('id', campaign.id)
                }
                or {
                    and {
                        isNull('startTime')
                        isNull('endTime')
                    }
                    and {
                        isNull('startTime')
                        ge('endTime', now)
                    }
                    and {
                        isNull('endTime')
                        le('startTime', now)
                    }
                    and {
                        le('startTime', now)
                        ge('endTime', now)
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug "Found [${result.size()}] pending recipients"
        }

        // 300 ms between each email means 2 minutes processing every 10 minutes.
        def sleep = grailsApplication.config.crm.campaign.email.sleep ?: 300L
        def proxy = grailsApplication.mainContext.getBean('crmEmailCampaignService')
        for (CrmCampaignRecipient r in result) {
            proxy.sendToRecipient(r)
            Thread.sleep(sleep)
        }
    }

    @Transactional
    void sendToRecipient(final CrmCampaignRecipient r) {
        def reply
        try {
            reply = event(for: "crmCampaign", topic: "sendMail", fork: false,
                    data: [tenant: r.campaign.tenantId, campaign: r.campaign.id, id: r.id, email: r.email, ref: r.ref])?.value
        } catch (Exception e) {
            log.error("Failed to send email to [${r.email}]", e)
            reply = e.message ?: 'Unknown error'
        }

        CrmCampaignRecipient.executeUpdate("update CrmCampaignRecipient set reason = ?, dateSent = ? where id = ?",
                [reply, new Date(), r.id])

        if (!reply) {
            event(for: "crmCampaign", topic: "sentMail", fork: true,
                    data: [tenant: r.campaign.tenantId, campaign: r.campaign.id, id: r.id, email: r.email, ref: r.ref])
        }
    }

    @Transactional
    void collectHyperlinks(final CrmCampaign campaign, final String body) {
        final Object html = new XmlSlurper(new Parser()).parseText(body)
        final Collection links = html.depthFirst().findAll { it.name() == 'a' }
        final Set delete = [] as Set
        if (campaign.trackables) {
            delete.addAll(campaign.trackables)
        }
        links.each { a ->
            String href = a.@href?.toString()
            String alt = a.@alt?.toString()
            String text = a.text()
            if (!href.startsWith('mailto')) {
                def link = CrmCampaignTrackable.findByCampaignAndHref(campaign, href)
                if (link) {
                    link.alt = alt
                    link.text = text
                    link.save()
                    delete.remove(link)
                } else {
                    campaign.addToTrackables(href: href, alt: alt, text: text)
                }
            }
        }
        for (d in delete) {
            campaign.removeFromTrackables(d)
            d.delete()
        }
    }

    String replaceHyperlinks(final CrmCampaignRecipient recipient, final String input) {
        final String serverURL = grailsApplication.config.crm.web.url ?: grailsApplication.config.grails.serverURL
        final CrmCampaign campaign = recipient.campaign
        final Object html = new XmlSlurper(new Parser()).parseText(input)
        final Collection links = html.depthFirst().findAll { it.name() == 'a' }
        for (a in links) {
            String href = a.@href?.toString()
            if (!href.startsWith('mailto')) {
                def link = CrmCampaignTrackable.findByCampaignAndHref(campaign, href)
                if (link) {
                    a.@href = "${serverURL}/t/${link.guid}/${recipient.guid}.htm".toString()
                }
            }
        }
        new StreamingMarkupBuilder().bind {
            mkp.declareNamespace("": "http://www.w3.org/1999/xhtml")
            mkp.yield html
        }
    }

    @Transactional
    def track(final String recipientGuid) {
        def recipient = CrmCampaignRecipient.findByGuid(recipientGuid)
        if (recipient) {
            if (log.isDebugEnabled()) {
                log.debug("Tracking ${recipient}")
            }
            if (!recipient.dateOpened) {
                recipient = CrmCampaignRecipient.lock(recipient.ident())
                if (!recipient.dateOpened) {
                    recipient.dateOpened = new Date()
                    recipient.save()
                }
            }
        } else {
            log.warn("No such recipient $recipientGuid")
        }
        return recipient
    }


    @Transactional
    String link(String recipientGuid, String trackableGuid, String addr) {
        // TODO make sure these statements do not affect the user in any way.
        def recipient = recipientGuid ? CrmCampaignRecipient.findByGuid(recipientGuid) : null
        if (recipient) {
            if (!recipient.dateOpened) {
                // If user clicks a link in the message, it must be opened.
                recipient.discard()
                recipient = CrmCampaignRecipient.lock(recipient.ident())
                if (!recipient.dateOpened) {
                    recipient.dateOpened = new Date()
                    recipient.save()
                }
            }
        } else if (recipientGuid) {
            log.warn("Recipient $recipientGuid not found")
        }
        def href
        if (trackableGuid) {
            def link = CrmCampaignTrackable.findByGuid(trackableGuid)
            if (link) {
                href = link.href
                if (recipient) {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Click on $link")
                        }
                        new CrmCampaignTracker(trackable: link, recipient: recipient, ip: addr).save()
                    } catch (Exception e) {
                        log.error("Failed to link $trackableGuid/$recipientGuid", e)
                    }
                }
            } else {
                log.warn("No such link $trackableGuid")
            }
        }
        if ((!href) && recipient) {
            def campaign = recipient.campaign
            href = grailsLinkGenerator.link(controller: 'crmCampaignTracker', action: 'newsletter', params: [id: campaign.id, r: recipient.id], absolute: true)
        }
        return href
    }

    void optOut(CrmCampaignRecipient recipient, Collection<String> tags = []) {
        if (!recipient.dateOptOut) {
            CrmCampaignRecipient.withTransaction {
                recipient.discard()
                recipient = CrmCampaignRecipient.lock(recipient.ident())
                if (!recipient.dateOptOut) {
                    recipient.dateOptOut = new Date()
                    if (!recipient.dateOpened) {
                        // If user clicks the opt-out link in the message, it must be opened.
                        recipient.dateOpened = recipient.dateOptOut// If user clicks the opt-out link in the message, it must be opened.
                    }
                    recipient.save(flush: true)
                }
            }
            event(for: "crmCampaign", topic: "optout", fork: true,
                    data: [tenant: recipient.campaign.tenantId, campaign: recipient.campaign.id,
                            id: recipient.id, email: recipient.email, ref: recipient.ref, tags: tags])
        }
    }

    CrmCampaign getCampaignByPublicId(String publicId) {
        def idLength = Integer.valueOf(publicId[0])
        def primaryKey = Long.valueOf(publicId[1..idLength])
        def date = new SimpleDateFormat('yyMMddHHmmss').parse(publicId[(idLength + 1)..-1])
        CrmCampaign.createCriteria().get() {
            eq('id', primaryKey)
            eq('dateCreated', date)
            cache true
        }
    }
}
