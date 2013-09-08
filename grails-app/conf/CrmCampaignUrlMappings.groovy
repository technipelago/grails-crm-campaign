class CrmCampaignUrlMappings {

	static mappings = {
        "/t/${id}.png" {
            controller = "crmCampaignTracker"
            action = "track"
            constraints {
                id(matches: /[0-9a-f\-]+/)
            }
        }
        "/t/${id}/${recipient}.${ext}" {
            controller = "crmCampaignTracker"
            action = "link"
            constraints {
                id(matches: /[0-9a-f\-]+/)
                recipient(matches: /[0-9a-f\-]+/)
                ext(matches: /[htmlpdfdocxst]+/)
            }
        }
        "/newsletter/${id}/${recipient}.htm" {
            controller = "crmCampaignTracker"
            action = "newsletter"
            constraints {
                id(matches: /[0-9]+/)
                recipient(matches: /[0-9a-f\-]+/)
            }
        }
        "/newsletter/${id}.htm" {
            controller = "crmCampaignTracker"
            action = "newsletter"
            constraints {
                id(matches: /[0-9]+/)
            }
        }
        "/optout/${id}.htm" {
            controller = "crmCampaignTracker"
            action = "optout"
            constraints {
                id(matches: /[0-9a-f\-]+/)
            }
        }
	}
}
