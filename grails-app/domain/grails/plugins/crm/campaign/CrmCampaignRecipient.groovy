package grails.plugins.crm.campaign

import grails.plugins.crm.core.UuidEntity

/**
 * Campaign Recipient
 */
@UuidEntity
class CrmCampaignRecipient {
    CrmCampaign campaign
    String ref
    String email
    Date dateCreated
    Date dateSent
    Date dateOpened
    Date dateOptOut
    Date dateBounced
    String reason

    static constraints = {
        dateCreated()
        dateSent(nullable: true)
        dateOpened(nullable: true)
        dateOptOut(nullable: true)
        dateBounced(nullable: true)
        ref(maxSize: 80, nullable: true)
        email(maxSize: 100, unique: 'campaign') // email:true removed since it does not support "Firstname Lastname <email@foo.com>"
        reason(maxSize: 255, nullable: true)
    }

    static mapping = {
        version false
        columns {
            ref index: 'idx_recipient_ref'
            dateSent index: 'idx_recipient_sent'
            // Unique index on campaign_id + email
            //campaign index: 'idx_recipient_mail'
            //email index: 'idx_recipient_mail'
        }
    }

    String toString() {
        email.toString()
    }
}
