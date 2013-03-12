package grails.plugins.crm.campaign

import grails.plugin.spock.IntegrationSpec
import groovy.json.JsonSlurper
import spock.lang.Shared

/**
 * Tests for campaign processor artifact "ProductDiscountCampaign".
 */
class ProductDiscountCampaignSpec extends IntegrationSpec {

    def crmCampaignService
    def productDiscountCampaign

    def setup() {
        productDiscountCampaign.crmProductService = [getProduct: { sku ->
            switch (sku) {
                case "3com":
                    return [number: sku, name: "3Com 8 port gigabit switch", price: 100, vat: 0.25, group: [name: "Switches", param: "switch"]]
                case "mbp15":
                    return [number: sku, name: "Macbook Pro 15\"", price: 2000, vat: 0.25, group: [name: "Portable Computers", param: "laptop"]]
                case "cat6":
                    return [number: sku, name: "CAT6 cable 3 meter", price: 12, vat: 0.25, group: [name: "Cables and converters", param: "cable"]]
                case "usb":
                    return [number: sku, name: "8GB USB stick", price: 9, vat: 0.25, group: [name: "External storage", param: "ext"]]
                case "discount":
                    return [number: sku, name: "Discount", price: 0, vat: 0.25, group: [name: "Discount items", param: "discount"]]
                default:
                    return null
            }
        }]
    }

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

    def "test execution without discount product"() {
        given: "mock a shopping cart and create a fresh campaign"
        def status = crmCampaignService.createCampaignStatus(name: "TEST", true)
        def campaign = crmCampaignService.createCampaign(number: "test1", name: "Get 10% off our network products", status: status)

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
        def data = [cart: cart, campaign: "test1"]
        def reply = productDiscountCampaign.process(data)

        then: "we got 10% discount on the switch"
        reply
        reply.modify.size() == 1
        reply.modify.head().id == "3com"
        reply.modify.head().discount == 0.1
        reply.modify.head().price == null
        reply.add == null
        reply.remove == null
    }

    def "test execution with a discount product"() {
        given: "mock a shopping cart and create a fresh campaign"
        def status = crmCampaignService.createCampaignStatus(name: "TEST", true)
        def campaign = crmCampaignService.createCampaign(number: "test2", name: "Get 10% off our network products", status: status)

        when: "configure the campaign as 10% off on switches, routers and firewalls. Add a specific discount item to the cart."
        productDiscountCampaign.configure(campaign) {
            productGroups = ['switch', 'router', 'firewall']
            discountProduct = "discount"
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
        def data = [cart: cart, campaign: "test2"]
        def reply = productDiscountCampaign.process(data)

        then: "we got 10% discount on the switch"
        reply
        reply.remove.size() == 1 // Since this is a replace operation, we first get a 'remove', then an 'add'.
        reply.remove.head().id == "discount"
        reply.add.size() == 1
        reply.add.head().id == "discount"
        reply.add.head().price == -20
        reply.add.head().discount == null
        reply.modify == null
    }

    def "test execution with a campaign product"() {
        given: "mock a shopping cart and create a fresh campaign"
        def status = crmCampaignService.createCampaignStatus(name: "TEST", true)
        def campaign = crmCampaignService.createCampaign(number: "test3", name: "Get a free USB stick when you buy our network products", status: status)

        when: "configure the campaign as 10% off on switches, routers and firewalls"
        productDiscountCampaign.configure(campaign) {
            productGroups = ['switch', 'router', 'firewall']
            discountProduct = "usb"
            discount = 0 // No % discount, this is a "get a free product" campaign.
        }
        campaign.save(failOnError: true)

        then: "campaign was saved"
        campaign.id != null
        campaign.handlerName == 'productDiscountCampaign'

        when: "apply the campaign on our shopping cart"
        def cart = [[id: "3com", quantity: 2, price: 100],
                [id: "mbp15", quantity: 1, price: 2000],
                [id: "cat6", quantity: 5, price: 12]]
        def data = [cart: cart, campaign: "test3"]
        def reply = productDiscountCampaign.process(data)

        then: "check that a USB stick was added to the shopping cart"
        reply
        reply.remove.size() == 1 // We first get a 'remove' otherwise it would add one USB stick for each invocation.
        reply.remove.head().id == "usb"
        reply.add.size() == 1
        reply.add.head().id == "usb"
        reply.add.head().price == 0.0
        reply.add.head().discount == null
        reply.modify == null
    }
}
