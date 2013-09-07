package grails.plugins.crm.campaign

/**
 * Outbound email configuration.
 */
class CrmEmailConfiguration {

    String subject
    String sender
    CrmCampaign campaign

    @Override
    String toString() {
        subject.toString()
    }
}
