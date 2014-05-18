package grails.plugins.crm.campaign

import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.tags.CrmTagLink
import groovy.transform.CompileStatic
import org.apache.commons.lang.StringUtils

import javax.mail.Message
import javax.mail.Multipart
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Read bounced email messages and try to find and update the matching Recipient.
 */
class CrmEmailBounceService {

    def grailsApplication
    def crmCoreService
    def crmImapService

    private static final Pattern EMAIL_PATTERN =
            ~/[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})/

    /**
     * Scan IMAP folder and look for bounced messages.
     *
     * @param host mail server
     * @param port IMAP port
     * @param username mailbox user
     * @param password mailbox password
     */
    void scan(String host, int port, String username, String password) {
        def config = grailsApplication.config

        final String inboxFolder = config.crm.campaign.email.bounce.imap.folder.inbox ?: "INBOX"
        final String archiveFolder = config.crm.campaign.email.bounce.imap.folder.archive ?: (inboxFolder + ".Archive")
        final String tag = config.crm.campaign.email.bounce.tag ?: null
        final Integer maxProcess = config.crm.campaign.email.bounce.maxProcess ?: 10000

        crmImapService.eachMessage(
                host: host,
                port: port,
                username: username,
                password: password,
                inbox: inboxFolder,
                archive: archiveFolder,
                max: maxProcess) { Message msg ->

            if (isPermanentFailure(msg)) {
                final String bodyText = getMessageText(msg)
                if (bodyText) {
                    final Matcher email = EMAIL_PATTERN.matcher(bodyText)
                    if (email.find()) {
                        def recipient = CrmCampaignRecipient.createCriteria().get() {
                            eq('email', email.group())
                            campaign {
                                eq('tenantId', TenantUtils.tenant)
                            }
                            order 'dateSent', 'desc'
                            maxResults 1
                        }
                        if (recipient) {
                            log.debug "Found recipient [$recipient]"
                            if (!recipient.dateBounced) {
                                recipient.dateBounced = msg.getSentDate() ?: new Date()
                                recipient.reason = StringUtils.abbreviate(bodyText, 255)
                                recipient.save()
                                if (tag && recipient.ref) {
                                    def exists = CrmTagLink.createCriteria().count() {
                                        eq('ref', recipient.ref)
                                        eq('value', tag)
                                    }
                                    if (!exists) {
                                        def reference = crmCoreService.getReference(recipient.ref)
                                        if (reference) {
                                            reference.setTagValue(tag)
                                            if (reference.hasProperty('email')) {
                                                reference.email = null
                                            }
                                            reference.save()
                                            log.debug "Tagged [$reference] with [$tag]"
                                        } else {
                                            log.warn("Email recipient [${recipient.ref}] not found")
                                        }
                                    }
                                }
                            }
                            return true
                        } else {
                            log.debug "Recipient [${email.group()}] not found"
                        }
                    } else {
                        log.debug "No email pattern found in message body: $bodyText"
                    }
                }
            } else if (isTemporaryFailure(msg)) {
                return true
            }
            return false
        }
    }

    @CompileStatic
    private String getMessageText(final Message msg) {
        def text = new StringBuilder()
        def ok = true
        try {
            def content = msg.getContent()
            if (content instanceof Multipart) {
                for (int i = 0; i < content.getCount(); i++) {
                    def p = content.getBodyPart(i)
                    if (i == 0) {
                        def p0 = p.getContent()
                        if (p0 instanceof Multipart) {
                            text << p0.getBodyPart(0).getContent().toString()
                        } else if (p0 instanceof InputStream) {
                            p0.eachLine { line ->
                                text << line
                            }
                        } else {
                            text << p0.toString()
                        }
                    }
                }
            } else if (content instanceof InputStream) {
                content.eachLine { line ->
                    text << line
                }
            } else {
                text << content.toString()
            }
        } catch (Exception e) {
            log.error("Exception while reading email from ${msg.from[0]}", e)
            ok = false
        }

        return ok ? text.toString() : null
    }

    /**
     * Check if message informs us it's a permanent failure.
     * @param msg the message
     * @return true if permanent
     */
    @CompileStatic
    private boolean isPermanentFailure(Message msg) {
        def sender = msg.from[0].toString().toLowerCase()
        if (sender.startsWith('mailer-daemon')) {
            log.debug "Sender is 'mailer-daemon', looks like a bounce"
            return true
        }
        def subject = msg.subject?.toLowerCase() ?: ''
        if (subject.startsWith('delivery status notification') || subject.startsWith('delivery failure')) {
            log.debug "Subject [$subject] looks like a bounce"
            return true
        }
        if (subject.startsWith('undeliverable') || subject.startsWith('undelivered')) {
            log.debug "Subject [$subject] looks like a bounce"
            return true
        }
        if (subject.startsWith('olevererbart')) {
            log.debug "Subject [$subject] looks like a bounce"
            return true
        }
        log.trace "Subject [$subject] looks normal"
        return false
    }

    /**
     * Check if message informs us it's a temporary failure.
     * @param msg the message
     * @return true if temporary
     */
    @CompileStatic
    private boolean isTemporaryFailure(Message msg) {
        def subject = msg.subject?.toLowerCase() ?: ''
        // Catch the following subjects:
        // Out of Office
        // Frånvaro
        // Autosvar
        // AUTO
        if (subject.startsWith('out of office') || subject.startsWith('frånvaro') || subject.startsWith('auto')) {
            log.debug "Subject [$subject] looks like a temporary failure"
            return true
        }
        return false
    }

}
