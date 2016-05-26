package grails.plugins.crm.campaign

import grails.plugins.crm.core.UuidEntity

/**
 * Campaign Recipient
 */
@UuidEntity
class CrmCampaignRecipient {

    public static final List BIND_WHITELIST = ['campaign', 'ref', 'name', 'email', 'telephone']

    CrmCampaign campaign
    String ref
    String name
    String email
    String telephone
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
        // email:true removed since it does not support Firstname Lastname <email@foo.com>
        name(maxSize: 100, nullable: true)
        email(maxSize: 100, nullable: true)
        telephone(maxSize: 32, nullable: true)
        reason(maxSize: 255, nullable: true)
    }

    static mapping = {
        version false
        columns {
            guid index: 'idx_recipient_guid'
            ref index: 'idx_recipient_ref'
            dateSent index: 'idx_recipient_sent'
            // Create index on campaign_id + email
            campaign index: 'idx_recipient_mail,idx_recipient_tel'
            email index: 'idx_recipient_mail'
            // Create index on campaign_id + telephone
            telephone index: 'idx_recipient_tel'
        }
    }

    static transients = ['address']

    def beforeDelete() {
        CrmCampaignRecipient.withNewSession {
            CrmCampaignTracker.findAllByRecipient(this)*.delete(flush: true)
        }
    }

    transient void setAddress(String arg) {
        email = address
    }

    transient String getAddress() {
        email
    }

    transient Map<String, Object> getDao() {
        final Map<String, Object> map = [tenant: campaign.tenantId, guid: guid, campaign: campaign.getDao(),
                                         name: name, email: email, telephone: telephone]
        if(ref) {
            map.ref = ref
        }
        map
    }

    String toString() {
        final StringBuilder s = new StringBuilder()
        if(name) {
            s.append(name)
        }
        if(telephone) {
            if(s.length()) {
                s.append(', ')
            }
            s.append(telephone)
        }
        if(email) {
            if(s.length()) {
                s.append(' <')
            }
            s.append(email)
            if(name || telephone) {
                s.append('>')
            }
        }
        return s.toString()
    }
}
