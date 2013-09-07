package grails.plugins.crm.campaign

import grails.plugins.crm.content.CrmResourceRef
import org.hibernate.Cache
import org.springframework.transaction.annotation.Transactional

/**
 * Service for outbound email campaigns.
 */
class CrmEmailCampaignService {

    static transactional = false

    def grailsApplication
    def sessionFactory
    def crmContentService

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
    CrmResourceRef setEmailBody(CrmCampaign campaign, InputStream bodyContent, String contentType, Map params = [:]) {
        def filename = contentType.contains('html') ? "body.html" : "body.txt"
        def bodyTemplate = crmContentService.getAttachedResource(campaign, filename)
        if (bodyTemplate) {
            crmContentService.updateResource(bodyTemplate, bodyContent, contentType)
        } else {
            if (!params.title) {
                params.title = "E-postmeddelandets innehåll"
            }
            if (!params.status) {
                params.status = "published"
            }
            bodyTemplate = crmContentService.createResource(bodyContent, filename, -1, contentType, campaign, params)
        }
        bodyTemplate
    }

}
