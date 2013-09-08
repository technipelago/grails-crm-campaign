package grails.plugins.crm.campaign

/**
 * Each click on a link creates an instance of this class.
 */
class CrmCampaignTracker {
    Date dateCreated
    CrmCampaignRecipient recipient
    CrmCampaignTrackable trackable
    String ip

    static constraints = {
        recipient()
        trackable()
        ip(maxSize: 40, nullable: true)
    }

    static mapping = {
        version false
    }

    String toString() {
        "${dateCreated.format('yyyy-MM-dd HH:mm:ss')} $recipient $trackable"
    }
}
