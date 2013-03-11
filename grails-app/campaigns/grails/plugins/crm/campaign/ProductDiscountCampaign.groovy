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

import grails.util.ClosureToMapPopulator
import grails.util.GrailsNameUtils
import groovy.json.JsonSlurper

/**
 * A campaign processor that give product discounts in the web shop, based on rules.
 */
class ProductDiscountCampaign {

    public static final String CONDITION_NONE = 'none'
    public static final String CONDITION_ANY = 'any'
    public static final String CONDITION_ALL = 'all'

    def crmCampaignService
    def crmProductService // TODO soft reference to service that we have no dependency on in BuildConfig.groovy

    void configure(CrmCampaign campaign, Closure arg) {
        def map = new ClosureToMapPopulator().populate(arg)
        campaign.handlerName = GrailsNameUtils.getPropertyName(getClass())
        campaign.handlerConfig = groovy.json.JsonOutput.toJson(map)
    }

    def process(data) {
        def cart = data.cart
        def reply
        if (cart && data.campaign) {
            def campaign = crmCampaignService.findByCode(data.campaign, GrailsNameUtils.getPropertyName(getClass()))
            if (campaign) {
                def cfg = new JsonSlurper().parseText(campaign.handlerConfig)
                def discountProduct = cfg.discountProduct ? crmProductService.getProduct(cfg.discountProduct) : null
                def cfgDiscount = cfg.discount ? Double.valueOf(cfg.discount.toString()) : 0.0
                def cfgThreshold = cfg.threshold ? Double.valueOf(cfg.threshold.toString()) : 0.0
                def total = 0
                def found = [:]
                for (p in cart) {
                    if (cfg.productGroups) {
                        def crmProduct = crmProductService.getProduct(p.id)
                        if (crmProduct && cfg.productGroups.contains(crmProduct.group.param)) {
                            found[p.id] = true
                        }
                    } else if (cfg.products) {
                        if (cfg.products.contains(p.id)) {
                            found[p.id] = true
                        }
                    } else {
                        found[p.id] = true
                    }
                    if (found[p.id]) {
                        total += ((p.price ?: 0) * (p.quantity ?: 0))
                    }
                    if (found[p.id] && !discountProduct) {
                        if (!reply) {
                            reply = [:]
                        }
                        reply.get('modify', []) << [id: p.id, discount: cfgDiscount]
                    }
                }
                if (total > cfgThreshold && (cfg.condition?.toLowerCase() != CONDITION_ALL || found.size() == cfg.products.size())) {
                    if (discountProduct) {
                        def discount = cfgDiscount < 1 ? total * cfgDiscount : cfgDiscount
                        reply = [remove: [[id: discountProduct.number]],
                                add: [[id: discountProduct.number, quantity: 1, price: -discount, vat: discountProduct.vat, comment: discountProduct.description]]]
                    }
                } else if (discountProduct) {
                    // Total cart value did not not reach threshold, remove discount product.
                    reply = [remove: [[id: discountProduct.number]]]
                } else {
                    // Total cart value did not not reach threshold, cancel all discounts.
                    reply = null
                }
            }
        }
        return reply
    }
}
