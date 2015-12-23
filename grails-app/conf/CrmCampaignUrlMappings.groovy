class CrmCampaignUrlMappings {

	static mappings = {
        name 'crm-track-beacon': "/t/${id}.png" {
            controller = "crmCampaignTracker"
            action = "track"
            constraints {
                id(matches: /[0-9a-f\-]+/)
            }
        }
        name 'crm-track-click': "/t/${id}/${recipient}.${ext}" {
            controller = "crmCampaignTracker"
            action = "link"
            constraints {
                id(matches: /[0-9a-f\-]+/)
                recipient(matches: /[0-9a-f\-]+/)
                ext(matches: /[htmlpdfdocxst]+/)
            }
        }
        name 'crm-newsletter': "/newsletter/${id}/${recipient}.html" {
            controller = "crmCampaignTracker"
            action = "newsletter"
            constraints {
                id(matches: /[0-9]+/)
                recipient(matches: /[0-9a-f\-]+/)
            }
        }
        name 'crm-newsletter-anonymous': "/newsletter/${id}.html" {
            controller = "crmCampaignTracker"
            action = "newsletter"
            constraints {
                id(matches: /[0-9]+/)
            }
        }
        name 'crm-optout': "/optout/${id}.html" {
            controller = "crmCampaignTracker"
            action = "optout"
            constraints {
                id(matches: /[0-9a-f\-]+/)
            }
        }
	}
}
