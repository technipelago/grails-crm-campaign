package grails.plugins.crm.campaign

import grails.plugins.crm.core.TenantUtils

/**
 * Read bounced email messages and try to find and update the matching Recipient.
 */
class CrmEmailBounceJob {
    // wait 15 minutes before first timeout, then execute job every six hours.
    static triggers = { simple(name: 'emailBouncer', startDelay: 1000 * 60 * 15, repeatInterval: 1000 * 60 * 60 * 6) }

    def group = 'email'
    def concurrent = false

    def grailsApplication
    def crmEmailBounceService

    def execute() {
        def config = grailsApplication.config
        if (config.crm.campaign.job.bounce.enabled) {

            final String host = config.crm.campaign.email.bounce.imap.host?.toString()
            final Integer port = config.crm.campaign.email.bounce.imap.port ?: 143
            final String username = config.crm.campaign.email.bounce.imap.username?.toString()
            final String password = config.crm.campaign.email.bounce.imap.password?.toString()
            final Long tenant = config.crm.campaign.email.bounce.tenant ?: 1L

            if (host && username && password) {
                TenantUtils.withTenant(tenant) {
                    crmEmailBounceService.scan(host, port, username, password)
                }
            } else {
                log.warn("No mail account configured for email bounce processing")
            }
        } else {
            log.info("CrmEmailBounceJob disabled in environment ${grails.util.Environment.current.name}")
        }
    }
}
