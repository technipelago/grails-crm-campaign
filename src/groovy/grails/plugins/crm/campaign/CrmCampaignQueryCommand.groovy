package grails.plugins.crm.campaign

import grails.validation.Validateable

@Validateable
class CrmCampaignQueryCommand implements Serializable {
    String handlerName
    String parent
    String number
    String name
    String username
    String fromDate
    String toDate
}
