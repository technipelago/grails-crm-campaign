package grails.plugins.crm.campaign

import grails.test.spock.IntegrationSpec
import test.TestEntity

/**
 * Test campaign target groups.
 */
class CampaignTargetSpec extends IntegrationSpec {

    def crmCampaignService
    def crmCampaignTargetService
    def gormSelection

    def "Create and select target group on a campaign"() {
        given: "create a fresh campaign"
        def status = crmCampaignService.createCampaignStatus(name: "TEST", true)
        def campaign = crmCampaignService.createCampaign(number: "test1", name: "Invitation to our test party", status: status, true)
        def backup = gormSelection.getCriteria(TestEntity)
        gormSelection.setCriteria(TestEntity) { query, params ->
            if (query.name) {
                ilike('name', '%' + query.name + '%')
            }
            if (query.age) {
                if (query.age[0] == '<') {
                    lt('age', Integer.valueOf(query.age[1..-1]))
                } else if (query.age[0] == '>') {
                    gt('age', Integer.valueOf(query.age[1..-1]))
                } else if (query.age.indexOf('-') != -1) {
                    def (from, to) = query.age.split('-').toList()
                    between('age', Integer.valueOf(from), Integer.valueOf(to))
                } else {
                    eq('age', Integer.valueOf(query.age))
                }
            }
        }

        when:
        new TestEntity(name: "Joe Average", age: 40).save(flush: true)
        new TestEntity(name: "Linda Average", age: 37).save(flush: true)
        new TestEntity(name: "Jason Average", age: 11).save(flush: true)
        new TestEntity(name: "Lisa Average", age: 9).save(flush: true)
        new TestEntity(name: "Ben Average", age: 63).save(flush: true)
        new TestEntity(name: "Mary Average", age: 65).save(flush: true)

        then:
        TestEntity.count() == 6

        when: "Add target group: people over 40"
        crmCampaignTargetService.addSelection(campaign, new URI("gorm://testEntity/list?age=" + ">40".encodeAsURL()))

        then:
        campaign.save()
        campaign.target.size() == 1

        when:
        def result = crmCampaignTargetService.select(campaign, [:])

        then: "Ben and Mary are over 40"
        result.size() == 2

        when: "Remove B-people"
        crmCampaignTargetService.addSelection(campaign, new URI("gorm://testEntity/list?name=B"), CrmCampaignTarget.DIFF)

        then:
        campaign.save()
        campaign.target.size() == 2

        when:
        result = crmCampaignTargetService.select(campaign, [:])

        then:
        result.size() == 1
        result.find{it}.name == "Mary Average"
    }
}
