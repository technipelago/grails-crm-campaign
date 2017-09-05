package grails.plugins.crm.campaign

import grails.plugins.crm.content.CrmResourceRef
import grails.transaction.Transactional
import groovy.xml.StreamingMarkupBuilder
import org.apache.commons.lang.StringUtils
import org.ccil.cowan.tagsoup.Parser
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource

/**
 * Service for outbound email campaigns.
 */
class CrmEmailCampaignService {

    private static final String BODY_PART = 'body.html'
    private static final String HANDLER_NAME = 'emailCampaign'

    static transactional = false

    private static byte[] beaconBytes

    def grailsApplication
    def crmCoreService
    def jobManagerService
    def crmContentService
    def crmContentRenderingService
    def crmCampaignService
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

    def send(CrmCampaign crmCampaign = null) {
        final Date now = new Date()
        if (log.isDebugEnabled()) {
            log.debug "Checking pending recipients at $now"
        }
        def batchSize = grailsApplication.config.crm.campaign.email.batch.size ?: 500 // 3000 emails per hour.
        final List<CrmCampaignRecipient> result = CrmCampaignRecipient.createCriteria().list([max: batchSize]) {
            isNotNull('email') // email is forced at insert but this could be a hint for the query optimizer.
            isNull('dateSent')
            isNull('reason')
            campaign {
                eq('handlerName', HANDLER_NAME)
                if (crmCampaign) {
                    eq('id', crmCampaign.id)
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
        def proxy = grailsApplication.mainContext.getBean(this.class)
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

        // A null reply indicates that the email was sent ok and we trigger an "sentMail" event
        // that listeners can use for logging purposes, etc.
        if (!reply) {
            event(for: "crmCampaign", topic: "sentMail", fork: true,
                    data: [tenant: r.campaign.tenantId, campaign: r.campaign.id, id: r.id, email: r.email, ref: r.ref])
        }
    }

    @Transactional
    void collectHyperlinks(final CrmCampaign campaign) {
        Set delete = [] as Set
        if (campaign.trackables) {
            delete.addAll(campaign.trackables)
        }
        List allParts = getAllParts(campaign)
        for (part in allParts) {
            final String body = part.text
            final Object html = new XmlSlurper(new Parser()).parseText(body)
            final Collection links = html.depthFirst().findAll { it.name() == 'a' }

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

    private boolean isAddBeacon(cfg) {
        if (cfg != null) {
            return Boolean.valueOf(cfg.toString())
        }
        grailsApplication.config.crm.campaign.email.track
    }

    String replaceHyperlinks(final CrmCampaignRecipient recipient, final String input, final Map configuration) {
        final String serverURL = getBaseUrl()
        final CrmCampaign campaign = recipient.campaign
        final Object html = new XmlSlurper(new Parser()).parseText(input)
        final Collection links = html.depthFirst().findAll { it.name() == 'a' }
        String beaconSrc = isAddBeacon(configuration.track) ? grailsLinkGenerator.link(mapping: 'crm-track-beacon',
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

    /**
     * The campaign's business key is created from id and dateCreated.
     * Format: [length of id][id][dateCreated]
     * Example: A campaign with id=42 and created=2016-02-19 20:44 will have the business key: "2421602192044".
     *
     * @param publicId the campaign's business key
     * @return a CrmCampaign instance or null if not found
     */
    @Transactional(readOnly = true)
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

    Map getPreviewModel(CrmCampaign campaign) {
        event(for: 'crmEmailCampaign', topic: 'previewModel', fork: false,
                data: [tenant: campaign.tenantId, campaign: campaign.id, user: 'nobody']).waitFor(5000)?.value
    }

    String render(CrmCampaign campaign, CrmCampaignRecipient recipient = null, Map userModel = null) {
        if (campaign == null) {
            campaign = recipient?.campaign
            if (campaign == null) {
                throw new IllegalArgumentException("campaign must be specified")
            }
        }

        def tenant = campaign.tenantId
        def model = [:]

        // The model is first populated with all properties from the (optional) reference domain instance.
        if (recipient?.ref) {
            def reference = crmCoreService.getReference(recipient.ref)
            if (reference) {
                if (reference.hasProperty('dao')) {
                    model.putAll(reference.dao) // Reference object must have a getDao() method.
                } else {
                    model.reference = reference
                }
            }
        }

        // The we add properties from the recipient instance (including 'email').
        if (recipient) {
            model.putAll(recipient.dao)
        } else {
            model.campaign = campaign.dao
            model.tenant = tenant
        }

        // And then configuration data for the campaign.
        Map cfg = campaign.configuration
        model.putAll(cfg)

        // And finally the (optional) user supplied model.
        if (!userModel) {
            userModel = getPreviewModel(campaign)
        }
        if(userModel) {
            model.putAll(userModel)
        }

        CrmResourceRef templateInstance
        String templateName = cfg.template
        if (templateName) {
            templateInstance = crmContentService.getContentByPath(templateName, tenant)
        } else {
            templateName = BODY_PART
            templateInstance = getPart(campaign, templateName)
        }

        String content
        if (templateInstance) {
            final StringWriter s = new StringWriter()
            crmContentRenderingService.render(templateInstance, model, 'freemarker', s)
            content = s.toString()
        } else {
            log.warn("Template [$templateName] referenced by campaign [${campaign.id}] not found")
            return "Template not found: $templateName"
        }

        recipient ? replaceHyperlinks(recipient, content, cfg) : content
    }

    @Transactional(readOnly = true)
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

    private List<CrmResourceRef> getAllParts(CrmCampaign crmCampaign) {
        // TODO filter on resource status (STATUS_PUBLISHED)
        crmContentService.findResourcesByReference(crmCampaign, [name: '*.html', sort: 'name', order: 'asc'])
    }

    @Transactional(readOnly = true)
    CrmResourceRef getPart(final CrmCampaign crmCampaign, String partName) {
        if (!partName.contains('.')) {
            partName = partName + '.html'
        }
        crmCampaignService.getCampaignResource(crmCampaign, partName)
    }

    @Transactional
    CrmResourceRef setPart(final CrmCampaign crmCampaign, String partName, String html) {
        if (html == null) {
            html = ''
        }
        if (!partName.contains('.')) {
            partName = partName + '.html'
        }
        def p = getPart(crmCampaign, partName)
        if (p) {
            def is = new ByteArrayInputStream(html.getBytes('UTF-8'))
            crmContentService.updateResource(p, is)
            is.close()
        } else {
            p = crmContentService.createResource(html, partName, crmCampaign)
        }
        p
    }
}
