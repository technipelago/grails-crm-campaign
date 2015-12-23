import grails.plugins.crm.campaign.CrmCampaign

config = {
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

}
