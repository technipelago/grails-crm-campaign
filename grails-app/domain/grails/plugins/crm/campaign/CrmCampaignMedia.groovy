package grails.plugins.crm.campaign

import grails.plugins.texttemplate.TextTemplate

/**
 * Campaign Media contains media for a campaign, like email templates, banners, etc.
 */
class CrmCampaignMedia {

    TextTemplate template

    static belongsTo = [campaign: CrmCampaign]
}
