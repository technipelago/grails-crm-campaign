package grails.plugins.crm.campaign

import grails.plugins.crm.core.TenantUtils
import groovy.xml.StreamingMarkupBuilder
import org.apache.commons.lang.StringUtils
import org.ccil.cowan.tagsoup.Parser
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.transaction.annotation.Transactional

/**
 * Service for outbound email campaigns.
 */
class CrmEmailCampaignService {

    static transactional = false

    private static byte[] beaconBytes

    def grailsApplication
    def crmCoreService
    def jobManagerService
    def crmContentService
    def crmContentRenderingService
    def grailsLinkGenerator

    public byte[] getBeaconImage() {
        if (beaconBytes == null) {
            synchronized (this) {
                if (beaconBytes == null) {
                    Resource resource = new ClassPathResource("tracker.png", grailsApplication.classLoader)
                    File file = resource.getFile()
                    beaconBytes = file.bytes
                }
            }
        }
        beaconBytes
    }

    def send(CrmCampaign campaign = null) {
        final Date now = new Date()
        final List<CrmCampaignRecipient> result = CrmCampaignRecipient.createCriteria().list([max: 500]) {
            // 3000 email / hour.
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

        // Be nice to the mail server and throttle emails.
        // 150 ms between each email means 2 minutes processing every 10 minutes.
        // Set to -1 for no sleep between emails.
        def sleep = grailsApplication.config.crm.campaign.email.sleep ?: 150L
        def proxy = grailsApplication.mainContext.getBean('crmEmailCampaignService')
        for (CrmCampaignRecipient r in result) {
            proxy.sendToRecipient(r)
            if (sleep > 0) {
                Thread.sleep(sleep)
            }
        }
    }

    @Transactional
    void sendToRecipient(final CrmCampaignRecipient r) {
        def reply
        try {
            reply = event(for: "crmCampaign", topic: "sendMail", fork: false,
                    data: [tenant: r.campaign.tenantId, campaign: r.campaign.id, id: r.id, email: r.email, ref: r.ref]).value
        } catch (Exception e) {
            log.error("Exception in sendMail event for [${r.email}]", e)
            reply = e.message ?: 'Unknown error'
        }

        if (reply) {
            int reasonLength = CrmCampaignRecipient.constraints.reason.maxSize
            if (reply.length() > reasonLength) {
                reply = StringUtils.abbreviate(reply, reasonLength)
            }
        }

        try {
            CrmCampaignRecipient.executeUpdate("update CrmCampaignRecipient set reason = ?, dateSent = ? where id = ?",
                    [reply, new Date(), r.id])
        } catch (Exception e) {
            log.error("Failed to update recipient status [$reply] for [${r.email}]", e)
            if (jobManagerService != null) {
                jobManagerService.pauseTrigger("email", "crmCampaignEmail")
                log.warn("Unscheduled quartz job email.crmCampaignEmail")
            }
        }

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

    private String getBaseUrl() {
        def webFrontUrl = grailsApplication.config.crm.web.url
        String url
        if (webFrontUrl) {
            url = webFrontUrl
        } else {
            url = grailsLinkGenerator.link(uri: '/', absolute: true).toString()
        }
        if (url.endsWith("/")) {
            url = url[0..-2]
        }
        url
    }

    String replaceHyperlinks(final CrmCampaignRecipient recipient, final String input) {
        final String serverURL = getBaseUrl()
        final CrmCampaign campaign = recipient.campaign
        final Object html = new XmlSlurper(new Parser()).parseText(input)
        final Collection links = html.depthFirst().findAll { it.name() == 'a' }
        String addBeacon = grailsApplication.config.crm.campaign.email.track
        String beaconSrc = addBeacon ? grailsLinkGenerator.link(mapping: 'crm-track-beacon',
                params: [id: recipient.guid], absolute: true) : null

        for (a in links) {
            String href = a.@href?.toString()
            if (!href.startsWith('mailto')) {
                def link = CrmCampaignTrackable.findByCampaignAndHref(campaign, href)
                if (link) {
                    a.@href = "${serverURL}/t/${link.guid}/${recipient.guid}.html".toString()
                }
            }
        }

        if (beaconSrc) {
            def body = html.body ?: html
            if (body) {
                body.appendNode({ img(src: beaconSrc, width: 1, height: 1) })
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
            href = "${getBaseUrl()}/newsletter/${recipient.campaign.publicId}/${recipient.guid}.html".toString()
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
                        recipient.dateOpened = recipient.dateOptOut
                    }
                    recipient.save(flush: true)
                }
            }
            event(for: "crmCampaign", topic: "optout", fork: true,
                    data: [tenant: recipient.campaign.tenantId, campaign: recipient.campaign.id,
                           id    : recipient.id, email: recipient.email, ref: recipient.ref, tags: tags])
        }
    }

    CrmCampaign getCampaignByPublicId(String publicId) {
        final Integer idLength = Integer.valueOf(publicId[0])
        final Long primaryKey = Long.valueOf(publicId[1..idLength])
        final CrmCampaign crmCampaign = CrmCampaign.get(primaryKey)
        if (crmCampaign) {
            final String timestamp = publicId[(idLength + 1)..-1]
            if (timestamp == crmCampaign.dateCreated.format('yyMMddHHmmss')) {
                return crmCampaign
            }
        }
        null
    }

    String render(CrmCampaign campaign, CrmCampaignRecipient recipient = null, Map userModel = [:]) {
        if (campaign == null) {
            campaign = recipient?.campaign
            if (campaign == null) {
                throw new IllegalArgumentException("campaign must be specified")
            }
        }

        Long tenant = campaign.tenantId
        def model = [tenant: tenant, campaign: campaign?.dao, recipient: recipient?.dao]
        def reference
        if(recipient?.ref) {
            model.reference = reference = crmCoreService.getReference(recipient.ref)
        }
        Map cfg = campaign.configuration
        model.putAll(cfg)
        if(userModel) {
            model.putAll(userModel)
        }

        // If the campaign is using a main template, process the template with FreeMarker.
        final String template = cfg.template
        String content
        if (template) {
            def templateInstance = crmContentService.getContentByPath(template, tenant)
            if (templateInstance) {
                final StringWriter s = new StringWriter()
                crmContentRenderingService.render(templateInstance, model, 'freemarker', s)
                content = s.toString()
            } else {
                log.warn("Template [${template}] referenced by campaign [${campaign.id}] not found")
                return
            }
        } else {
            final StringBuilder s = new StringBuilder()
            for (String p in cfg.parts) {
                String c = cfg[p]
                if (c) {
                    if (s.length()) {
                        s << '\n'
                    }
                    s << c
                }
            }
            content = s.toString()
            if(recipient) {
                // TODO parse content with Freemarker!
                content = content.replaceAll(/#ID#/, recipient.id.toString())
                if(reference.hasProperty('externalRef')) {
                    content = content.replaceAll(/#NUMBER#/, reference.externalRef)
                }
            }

        }

        recipient ? replaceHyperlinks(recipient, content) : content
    }

    Map getStatistics(CrmCampaign crmCampaign) {
        def props = ['dateCreated', 'dateSent', 'dateOpened', 'dateOptOut', 'dateBounced']
        def result = CrmCampaignRecipient.createCriteria().get() {
            eq('campaign', crmCampaign)
            projections {
                for (p in props) {
                    count(p)
                }
            }
            cache true
        }

        [props, result].transpose().collectEntries { it }
    }
}
