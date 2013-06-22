package grails.plugins.crm.campaign

import grails.plugins.crm.core.TenantUtils
import org.springframework.web.context.request.RequestContextHolder

import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_OK
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR

/**
 * Landing page router for public campaigns.
 */
class CrmCampaignLandingController {

    def crmCampaignService
    def crmContentService

    private static final int DEFAULT_CACHE_SECONDS = 60 * 60

    def index(Long t, String campaign) {
        def crmCampaign = TenantUtils.withTenant(t) { crmCampaignService.findByCode(campaign) }
        if (crmCampaign) {
            def campaignParam = grailsApplication.config.crm.campaign.sessionParam ?: 'campaign'
            request.session.setAttribute(campaignParam, crmCampaign.code ?: crmCampaign.number)
            def page = crmContentService?.findResourcesByReference(crmCampaign, [name: 'landing'])?.find { it }
            if (page) {
                def allResources = crmContentService.findResourcesByReference(crmCampaign)
                def content = [:]
                for (ref in allResources) {
                    if (ref.shared || (ref.published && (ref.tenantId == TenantUtils.tenant))) {
                        content[ref.name] = crm.createResourceLink(resource: ref)
                    }
                }
                return [crmCampaign: crmCampaign, page: page, resources: content]
            }
        }
        redirect uri: "/"
    }

    def file(Long t, String campaign, String file) {
        TenantUtils.withTenant(t) {
            def status = SC_NOT_FOUND
            def crmCampaign = crmCampaignService.findByCode(campaign)
            if (crmCampaign) {
                def ref = crmContentService?.findResourcesByReference(crmCampaign, [name: '=' + file])?.find { it }
                if (ref) {
                    try {
                        def publicCache = false
                        if (!(ref.shared || (ref.published && (ref.tenantId == TenantUtils.tenant)))) {
                            // TODO Check with crmSecurityService that user is logged in!!!
                            log.warn("$ref is not a shared resource, status is ${ref.statusText}")
                            response.sendError(SC_FORBIDDEN)
                            return
                        } else if (ref.shared) {
                            publicCache = true
                        }
                        def metadata = ref.metadata
                        def modified = metadata.modified.time
                        modified = modified - (modified % 1000) // Remove milliseconds.
                        response.setContentType(metadata.contentType)
                        response.setDateHeader("Last-Modified", modified)

                        def requestETag = request.getHeader("ETag")
                        if (requestETag && (requestETag == metadata.hash)) {
                            response.setStatus(SC_NOT_MODIFIED)
                            response.outputStream.flush()
                            return null
                        } else {
                            def ms = request.getDateHeader("If-Modified-Since")
                            if (modified <= ms) {
                                response.setStatus(SC_NOT_MODIFIED)
                                response.outputStream.flush()
                                return null
                            }
                        }

                        def len = metadata.bytes
                        response.setContentLength(len.intValue())
                        response.setHeader("ETag", metadata.hash)

                        if (request.method != "HEAD") {
                            def encoding = ref.encoding
                            if (encoding) {
                                response.setCharacterEncoding(encoding)
                            }
                            response.setHeader("Content-Disposition", "inline; filename=${ref.name}; size=$len")

                            def seconds = grailsApplication.config.crm.content.cache.expires ?: DEFAULT_CACHE_SECONDS
                            cacheThis(response, seconds, publicCache)

                            def out = response.outputStream
                            ref.writeTo(out)
                            out.flush()
                            out.close()
                        }
                        status = SC_OK
                    } catch (SocketException e) {
                        log.error("Client aborted while rendering resource: $t/$campaign/$file: ${e.message}")
                        status = SC_NO_CONTENT
                    } catch (IOException e) {
                        log.error("IOException while rendering resource: $t/$campaign/$file: ${e.message}")
                        status = SC_NO_CONTENT
                    } catch (Exception e) {
                        log.error("Error while rendering resource: $t/$campaign/$file", e)
                        status = SC_INTERNAL_SERVER_ERROR
                    }
                }
            }
            if (status == SC_OK) {
                RequestContextHolder.currentRequestAttributes().setRenderView(false)
            } else {
                response.sendError(status)
            }
        }

        return null
    }

    private void cacheThis(HttpServletResponse response, int seconds, boolean shared = false) {
        response.setHeader("Pragma", "")
        response.setHeader("Cache-Control", "${shared ? 'public' : 'private,no-store'},max-age=$seconds")
        Calendar cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, seconds)
        response.setDateHeader("Expires", cal.getTimeInMillis())
    }
}
