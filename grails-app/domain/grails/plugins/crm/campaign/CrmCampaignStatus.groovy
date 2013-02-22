package grails.plugins.crm.campaign

import grails.plugins.crm.core.AuditEntity
import grails.plugins.crm.core.CrmLookupEntity
import grails.plugins.crm.core.TenantEntity

/**
 * Campaign Status.
 */
@TenantEntity
@AuditEntity
class CrmCampaignStatus extends CrmLookupEntity {
    static transients = ['active']

    boolean isActive() {
        orderIndex.toString()[0] != '9'
    }
}