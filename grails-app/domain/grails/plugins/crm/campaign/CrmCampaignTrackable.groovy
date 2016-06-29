package grails.plugins.crm.campaign

import grails.plugins.crm.core.UuidEntity

/**
 * A trackable target, usually a hyperlink in an email.
 */
@UuidEntity
class CrmCampaignTrackable {

    String href
    String text
    String alt
    int archivedClicks

    static belongsTo = [campaign: CrmCampaign]

    static constraints = {
        href(maxSize: 255, blank: false)
        text(maxSize: 255, nullable: true)
        alt(maxSize: 255, nullable: true)
    }

    static mapping = {
        cache true
        columns {
            guid index: 'idx_trackable_guid'
        }
    }

    def beforeDelete() {
        CrmCampaignTrackable.withNewSession {
            CrmCampaignTracker.findAllByTrackable(this)*.delete(flush: true)
        }
    }

    String toString() {
        href.toString()
    }

}
