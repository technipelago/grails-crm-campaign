package grails.plugins.crm.campaign

import grails.validation.Validateable

@Validateable
class CrmCampaignQueryCommand implements Serializable {
    String parent
    String number
    String name
    String status
    String username
    String fromDate
    String toDate
}
