/*
 * Copyright (c) 2013 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.campaign

import grails.plugins.crm.core.AuditEntity
import grails.plugins.crm.core.TenantEntity
import grails.plugins.crm.core.UuidEntity
import grails.plugins.sequence.SequenceEntity

/**
 * Campaign project domain class.
 */
@TenantEntity
@AuditEntity
@UuidEntity
@SequenceEntity(property = "number", maxSize = 40, blank = false, unique = "tenantId")
class CrmCampaign {

    public static final List BIND_WHITELIST = ['number', 'name', 'description', 'status', 'parent']

    String name
    String description

    String username

    String handler
    String handlerConfig

    CrmCampaignStatus status
    CrmCampaign parent

    static hasMany = [children: CrmCampaign, codes: String]

    static constraints = {
        name(maxSize: 80, blank: false)
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        handler(maxSize: 80, nullable: true)
        handlerConfig(maxSize: 102400, nullable: true, widget: 'textarea')
        username(maxSize: 80, nullable: true)
        parent(nullable: true)
    }

    static mapping = {
        sort 'name': 'asc'
        number index: 'crm_campaign_number_idx'
        name index: 'crm_campaign_name_idx'
        handler index: 'crm_campaign_handler_idx'
        children sort: 'number', 'asc'
        codes joinTable: [name: 'crm_campaign_code', key: 'campaign_id', column: 'code'], cascade: 'all-delete-orphan'
    }

    static final List BIND_WHITELIST = ['number', 'name', 'description', 'username', 'parent'].asImmutable()

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true
    static relatable = true
    static auditable = true

    String toString() {
        name.toString()
    }

}
