package grails.plugins.crm.campaign

import grails.plugin.spock.IntegrationSpec
import groovy.json.JsonSlurper

/**
 * Tests for campaign processor artifact "ProductDiscountCampaign".
 */
class ProductDiscountCampaignSpec extends IntegrationSpec {

    def crmCampaignService
    def productDiscountCampaign

    def "test injection"() {
        expect: "productDiscountCampaign should be injected"
        productDiscountCampaign != null
    }

    def "test configuration"() {
        given: "create a fresh campaign"
        def campaign = new CrmCampaign()

        when: "configure the campaign using productDiscountCampaign"
        productDiscountCampaign.configure(campaign) {
            productGroups = ['switch', 'router', 'firewall']
            discount = 0.10
            condition = 'any'
        }

        then: "check that handleName was set"
        campaign.handlerName == 'productDiscountCampaign'
        campaign.handlerConfig != null

        when: "parse the config"
        def cfg = new JsonSlurper().parseText(campaign.handlerConfig)

        then: "check that we get the same config as we put in there"
        cfg.productGroups == ['switch', 'router', 'firewall']
        cfg.discount == 0.1
        cfg.condition == 'any'
        cfg.discountProduct == null
    }

    def "test execution without campaign product"() {
        given: "mock a shopping cart and create a fresh campaign"
        productDiscountCampaign.crmProductService = [getProduct: { sku ->
            switch (sku) {
                case "3com":
                    return [id: sku, name: "3Com 8 port gigabit switch", group: [name: "Switches", param: "switch"]]
                case "mbp15":
                    return [id: sku, name: "Macbook Pro 15\"", group: [name: "Portable Computers", param: "laptop"]]
                case "cat6":
                    return [id: sku, name: "CAT6 cable 3 meter", group: [name: "Cables and converters", param: "cable"]]
                default:
                    return null
            }
        }]
        def status = crmCampaignService.createCampaignStatus(name: "TEST", true)
        def campaign = crmCampaignService.createCampaign(number: "test01", name: "First test", code: "test", status: status)

        when: "configure the campaign as 10% off on switches, routers and firewalls"
        productDiscountCampaign.configure(campaign) {
            productGroups = ['switch', 'router', 'firewall']
            discount = 0.10
        }
        campaign.save(failOnError: true)

        then: "campaign was saved"
        campaign.id != null
        campaign.handlerName == 'productDiscountCampaign'

        when: "apply the campaign on our shopping cart"
        def cart = [[id: "3com", quantity: 2, price: 100],
                [id: "mbp15", quantity: 1, price: 2000],
                [id: "cat6", quantity: 5, price: 12]]
        def data = [cart: cart, campaign: "test"]
        def reply = productDiscountCampaign.process(data)

        then: "we got 10% discount on the switch"
        reply.modify.size() == 1
        reply.modify.head().id == "3com"
        reply.modify.head().discount == 0.1
        reply.modify.head().price == null
    }
}
