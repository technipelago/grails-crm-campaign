package grails.plugins.crm.campaign

import org.hibernate.HibernateException
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.dao.ConcurrencyFailureException

import java.text.SimpleDateFormat

/**
 * Track recipient activity.
 */
class CrmCampaignTrackerController {

    def crmEmailCampaignService

    // TODO Use @Cacheable to cache the image bytes.
    private void renderImage() {
        Resource resource = new ClassPathResource("tracker.png", grailsApplication.classLoader)
        File file = resource.getFile()
        response.setContentType("image/png")
        response.setContentLength(file.length().intValue())
        file.withInputStream { is ->
            response.getOutputStream() << is
        }
    }

    def track() {
        try {
            boolean keepTrying = true
            int retryCounter = 0

            while (keepTrying && retryCounter < 3) {
                try {
                    crmEmailCampaignService.track(params.id)
                    keepTrying = false
                } catch (ConcurrencyFailureException e) {
                    if (retryCounter >= 3) {
                        throw e
                    }
                    log.warn "Catched ConcurrencyFailureException for ${params.id} and retrying $retryCounter"
                } catch (HibernateException e) {
                    if (retryCounter >= 3) {
                        throw e
                    }
                    log.warn "Catched HibernateException for ${params.id} and retrying $retryCounter"
                } finally {
                    retryCounter++
                }
            }
        } catch (Exception e) {
            log.error("Failed to track ${params.id}", e)
        }
        renderImage()
    }

    def link() {
        def href
        try {
            boolean keepTrying = true
            int retryCounter = 0

            while (keepTrying && retryCounter < 3) {
                try {
                    href = crmEmailCampaignService.link(params.recipient, params.id, request.remoteAddr)
                    keepTrying = false
                } catch (ConcurrencyFailureException e) {
                    // TODO crmEmailCampaignService.link is currently catching all exceptions, so we will not enter this section.
                    if (retryCounter >= 3) {
                        throw e
                    }
                    log.warn "Catched OptimisticLockingFailureException for ${params.recipient}/${params.id} and retrying $retryCounter"
                } catch (HibernateException e) {
                    if (retryCounter >= 3) {
                        throw e
                    }
                    log.warn "Catched HibernateException for ${params.recipient}/${params.id} and retrying $retryCounter"
                } finally {
                    retryCounter++
                }
            }
        } catch (Exception e) {
            log.error("Failed to track ${params.id}", e)
        }

        if (href) {
            redirect(url: href)
        } else {
            response.sendError(404, "Not found")
        }
    }

    def optout() {
        try {
            def recipient = CrmCampaignRecipient.findByGuid(params.id)
            if (!recipient) {
                log.warn("No such recipient ${params.id}")
                response.sendError(404, "Not found")
                return
            }
            def campaign = recipient.campaign
            def configuration = campaign.configuration
            def css = configuration.style
            def opts = grailsApplication.config.crm.campaign.optout ?: [:]
            if (request.method == "POST") {
                def checked = params.list('opts')
                if (checked || !opts) {
                    log.debug("Opt-Out for ${recipient}")
                    recipient = crmEmailCampaignService.optOut(recipient, checked)
                }
            }
            return [recipient: recipient, campaign: campaign, cfg: configuration, css: css, opts: opts]
        } catch (Exception e) {
            log.error("Failed to opt-out ${params.id}", e)
            response.sendError(404, "Not found")
        }
    }

    def newsletter(String id, String recipient) {
        try {
            def campaign = crmEmailCampaignService.getCampaignByPublicId(id)
            if (!campaign) {
                log.warn("Campaign not found [$id]")
                response.sendError(404)
                return
            }
            def configuration = campaign.configuration
            def crmCampaignRecipient
            def body
            if (recipient) {
                crmCampaignRecipient = CrmCampaignRecipient.findByGuid(recipient)
                if (!crmCampaignRecipient) {
                    log.warn("recipient not found [$recipient]")
                    response.sendError(404)
                    return
                }
                if (crmCampaignRecipient.campaign != campaign) {
                    log.warn "SECURITY: recipient[${crmCampaignRecipient.id}].campaign[$id] != campaign[${campaign.id}]"
                    response.sendError(403)
                    return
                }
                crmEmailCampaignService.link(recipient, null, request.remoteAddr)
                body = crmEmailCampaignService.replaceHyperlinks(crmCampaignRecipient, configuration.html)
            } else {
                // Construct something that looks like a Recipient instance.
                crmCampaignRecipient = [campaign: campaign, guid: java.util.UUID.randomUUID().toString(),
                        email: '', telephone: '', dateCreated: campaign.dateCreated, dateSent: new Date(), dateOpened: new Date(),
                        contact: [firstName: '', lastName: '', name: '', username: '']]
                body = configuration.html
            }
            if (body) {
                return [body: body, campaign: campaign, recipient: crmCampaignRecipient, cfg: configuration]
            }
        } catch (Exception e) {
            log.error("Failed to render newsletter ${id}/${recipient}", e)
        }
        response.sendError(404)
    }
}
