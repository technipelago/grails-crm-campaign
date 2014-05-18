package grails.plugins.crm.campaign
/**
 * Read bounced email messages and try to find and update the matching Recipient.
 */
class CrmEmailBounceJob {
    // wait 15 minutes before first timeout, then execute job every four hours.
    static triggers = { simple(name: 'emailBouncer', startDelay: 1000 * 60 * 25, repeatInterval: 1000 * 60 * 60 * 4) }

    def group = 'email'
    def concurrent = false

    def grailsApplication
    def crmEmailBounceService

    def execute() {
        def config = grailsApplication.config
        if (config.crm.campaign.job.bounce.enabled) {

            final String host = config.crm.campaign.email.bounce.imap.host?.toString()
            final Integer port = config.crm.campaign.email.bounce.port ?: 143
            final String username = config.crm.campaign.email.bounce.imap.username?.toString()
            final String password = config.crm.campaign.email.bounce.imap.password?.toString()

            if (host && username && password) {
                crmEmailBounceService.scan(host, port, username, password)
            } else {
                log.warn("No mail account configured for email bounce processing")
            }
        } else {
            log.info("CrmEmailBounceJob disabled in environment ${grails.util.Environment.current.name}")
        }
    }
}
