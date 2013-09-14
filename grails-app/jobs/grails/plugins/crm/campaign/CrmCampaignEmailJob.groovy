package grails.plugins.crm.campaign

/**
 * Email campaign sender.
 */
class CrmCampaignEmailJob {
    // wait 5 minutes before first timeout, then execute job every 10 minutes.
    static triggers = { simple(name: 'crmCampaignEmail', startDelay: 1000 * 60 * 5, repeatInterval: 1000 * 60 * 10) }

    def group = 'email'
    def concurrent = false

    def crmEmailCampaignService

    def execute() {
        crmEmailCampaignService.send()
    }

}
