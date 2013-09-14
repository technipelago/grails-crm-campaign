package grails.plugins.crm.campaign

import groovy.transform.CompileStatic

/**
 * Campaign Target Group features.
 */
class CrmCampaignTargetService {

    def selectionService

    @CompileStatic
    private String getUniqueName(final Collection<CrmCampaignTarget> targets, final String name) {
        int revision = 1
        String tmp = name
        while (targets.find { CrmCampaignTarget t -> t.name.toLowerCase() == tmp.toLowerCase() }) {
            tmp = "$name ($revision)"
            revision++
        }
        return tmp
    }

    CrmCampaignTarget addSelection(CrmCampaign campaign, URI uri, int operation = 0, String name = null, String description = null) {
        def targets = campaign.target ?: []
        if (!name) {
            name = "Selection"
        }
        def t = new CrmCampaignTarget(campaign: campaign, orderIndex: (targets.max { it.orderIndex }?.orderIndex ?: 0) + 1, operation: operation,
                name: getUniqueName(targets, name), description: description, uri: uri)
        if (t.validate()) {
            campaign.addToTarget(t)
        } else {
            log.warn "Could not add target [$uri] to campaign [$campaign]"
        }
        return t
    }

    def select(query, params = null) {
        if (query == null) {
            throw new IllegalArgumentException("No query specified")
        }

        def campaign
        if (query instanceof CrmCampaign) {
            campaign = query
        } else {
            campaign = query.campaign
            if (!campaign) {
                throw new IllegalArgumentException("The query $query did not include 'campaign'")
            }
            if (!(campaign instanceof CrmCampaign)) {
                throw new IllegalArgumentException("Query value 'campaign' is not an instance of CrmCampaign [${campaign.class.name}]")
            }
        }

        Set all = [] as Set
        for (t in campaign.target?.sort { it.orderIndex }) {
            def result = selectionService.select(t.uri, params)
            switch (t.operation) {
                case CrmCampaignTarget.DIFF:
                    all.removeAll(result)
                    break
                case CrmCampaignTarget.INTERSECT:
                    all.retainAll(result)
                    break
                case CrmCampaignTarget.ADD:
                    all.addAll(result)
                    break
                default:
                    throw new IllegalArgumentException("Unknown target operation: ${t.operation}")
            }
            if (log.isDebugEnabled()) {
                log.debug "[CAMPAIGN-${campaign.ident()}] target operation [${t.operation}] [${t.uriString}] resulted in [${result.size()}] records, total size is now ${all.size()} records"
            }
        }
        return all
    }
}
