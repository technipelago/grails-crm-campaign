import grails.plugins.crm.campaign.CrmCampaign
import grails.plugins.crm.campaign.CrmCampaignStatus

grails.cache.config = {
    // Hibernate domain class second-level caches.
    domain {
        name CrmCampaign
        eternal false
        overflowToDisk false
        maxElementsInMemory 100
        maxElementsOnDisk 0
        timeToLiveSeconds 600
        timeToIdleSeconds 300
    }

    domain {
        name CrmCampaignStatus
        eternal false
        overflowToDisk false
        maxElementsInMemory 10
        maxElementsOnDisk 0
        timeToLiveSeconds 1800
        timeToIdleSeconds 900
    }

}