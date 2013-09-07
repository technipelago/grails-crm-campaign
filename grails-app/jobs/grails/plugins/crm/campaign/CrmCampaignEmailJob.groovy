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
        def now = new Date().clearTime()
        def result = CrmCampaignRecipient.createCriteria().list([max: 250]) { // 1500 email / hour.
            isNull('dateSent')
            isNull('reason')
            campaign {
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
                        le('startTime', now)
                        isNull('endTime')
                    }
                    and {
                        le('startTime', now)
                        ge('endTime', now)
                    }
                }
            }
        }
        for (r in result) {
            crmEmailCampaignService.sendToRecipient(r)
            Thread.sleep(300L) // 2 minutes processing every 10 minutes.
        }
    }

}
