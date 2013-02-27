package grails.plugins.crm.campaign

import grails.validation.Validateable

@Validateable
class CrmCampaignQueryCommand implements Serializable {
    String number
    String name
    String status
    String username
}
