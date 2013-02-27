package grails.plugins.crm.campaign

import grails.plugins.crm.core.TenantUtils

/**
 * Campaign Tags.
 */
class CrmCampaignTagLib {

    static namespace = "crm"

    def grailsApplication
    def crmCampaignService
    def groovyPagesTemplateEngine

    def campaign = { attrs, body ->
        def tenant = attrs.tenant ?: (TenantUtils.tenant ?: grailsApplication.config.textTemplate.defaultTenant)
        TenantUtils.withTenant(tenant) {
            if (attrs.campaign) {
                def crmCampaign = crmCampaignService.findByCode(attrs.campaign)
                if (crmCampaign) {
                    def resource = crmCampaignService.getCampaignResource(crmCampaign, attrs.name)
                    if (resource) {
                        def model = attrs.model ?: [:]
                        groovyPagesTemplateEngine.createTemplate(resource.text, "${attrs.name}").make(pageScope.variables + model).writeTo(out)
                        return
                    }
                }
            }
            out << tt.html(attrs, body) // Fall back to text template.
        }
    }
}
