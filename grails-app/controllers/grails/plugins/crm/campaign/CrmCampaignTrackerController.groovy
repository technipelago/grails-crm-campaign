package grails.plugins.crm.campaign

import org.hibernate.HibernateException
import org.springframework.dao.ConcurrencyFailureException

/**
 * Track recipient activity.
 */
class CrmCampaignTrackerController {

    def crmEmailCampaignService
    def grailsLinkGenerator

    private void renderImage() {
        byte[] image = crmEmailCampaignService.getBeaconImage()
        response.setContentLength(image.length)
        response.setContentType("image/png")
        response.getOutputStream().write(image)
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

    def optout(String id) {
        try {
            def recipient = CrmCampaignRecipient.findByGuid(id)
            if (!recipient) {
                log.warn("No such recipient ${id}")
                response.sendError(404, "Not found")
                return
            }
            def campaign = recipient.campaign
            def configuration = campaign.configuration
            def css = configuration.style
            def opts = grailsApplication.config.crm.campaign.optout ?: [:]
            if (request.post) {
                def checked = params.list('opts')
                if (checked || !opts) {
                    log.debug("Opt-Out for ${recipient}")
                    crmEmailCampaignService.optOut(recipient, checked)
                }
                redirect action: "newsletter", params: [id: campaign.publicId, recipient: recipient.guid]
                return
            }
            return [recipient: recipient, campaign: campaign, cfg: configuration, css: css, opts: opts]
        } catch (Exception e) {
            log.error("Failed to opt-out ${id}", e)
            response.sendError(404, "Not found")
        }
    }

    def newsletter(String id, String recipient) {
        try {
            def crmCampaign = crmEmailCampaignService.getCampaignByPublicId(id)
            if (!crmCampaign) {
                log.warn("Campaign not found [$id]")
                response.sendError(404)
                return
            }
            def crmCampaignRecipient
            if (recipient) {
                crmCampaignRecipient = CrmCampaignRecipient.findByGuid(recipient)
                if (!crmCampaignRecipient) {
                    log.warn("recipient not found [$recipient]")
                    response.sendError(404)
                    return
                }
                if (crmCampaignRecipient.campaign != crmCampaign) {
                    log.warn "SECURITY: recipient[${crmCampaignRecipient.id}].campaign[$id] != campaign[${crmCampaign.id}]"
                    response.sendError(403)
                    return
                }
                crmEmailCampaignService.link(recipient, null, request.remoteAddr)
            }

            def campaignConfig = crmCampaign.configuration
            def body = crmEmailCampaignService.render(crmCampaign, crmCampaignRecipient)
            if (body) {
                if (crmCampaignRecipient) {
                    final StringBuilder s = new StringBuilder()
                    // Body
                    s << body
                    // Footer
                    if(campaignConfig.optout) {
                        s << createOptOutLink(crmCampaignRecipient)
                    }
                    body = s.toString()
                }
                return [body: body, campaign: crmCampaign, recipient: crmCampaignRecipient, cfg: campaignConfig]
            }
        } catch (Exception e) {
            log.error("Failed to render newsletter ${id}/${recipient}", e)
        }
        response.sendError(404)
    }

    private String createOptOutLink(CrmCampaignRecipient recipient) {
        def webFrontUrl = grailsApplication.config.crm.web.url
        String ooLink
        if (webFrontUrl) {
            ooLink = "$webFrontUrl/optout/${recipient.guid}.html".toString()
        } else {
            ooLink = grailsLinkGenerator.link(mapping: 'crm-optout', params: [id: recipient.guid], absolute: true)
        }
        message(code: 'emailCampaign.optout.link',
                default: '<div style="font-size:smaller;"><a href="{1}">Cancel subscription</a>.</div>',
                args: [recipient.toString(), ooLink])
    }
}
