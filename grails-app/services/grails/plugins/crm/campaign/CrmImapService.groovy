package grails.plugins.crm.campaign

import groovy.transform.CompileStatic

import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.search.FlagTerm
import javax.mail.URLName

/**
 * Generic Email (IMAP) processing service.
 */
class CrmImapService {

    static transactional = false

    def grailsApplication

    void eachMessage(Map<String, Object> props, Closure work) {
        final Properties properties = props as Properties
        final Session session = Session.getInstance(properties)
        URLName urlName = new URLName(properties.store ?: 'imap', properties.host ?: 'localhost',
                properties.port ?: 143, null, properties.username, properties.password);

        final Store store = session.getStore(urlName)
        Folder inbox
        Folder archive
        try {
            final int maxProcess = properties.max ?: 1000000
            final String to = properties.to ?: null
            store.connect(properties.host, properties.username, properties.password)
            inbox = openFolder(store, properties.inbox)
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.DELETED), false))
            log.debug("Scanning ${messages.size()} messages in ${inbox.getName()}")
            int counter = 1
            for (Message msg in messages) {
                if (counter++ > maxProcess) {
                    break
                }
                if(log.isTraceEnabled()) {
                    log.trace("$counter Message from ${msg.from[0]} \"${msg.subject}\" to ${msg.getRecipients(Message.RecipientType.TO)}")
                }
                if(!to || msg.getRecipients(Message.RecipientType.TO).find{it.toString().contains(to)}) {
                    msg.setFlag(Flags.Flag.SEEN, true) // Mark message as read.
                    if (work(msg)) {
                        if (!archive) archive = openFolder(store, properties.archive)
                        inbox.copyMessages([msg] as Message[], archive)
                        msg.setFlag(Flags.Flag.DELETED, true)
                    }
                }
            }
        } finally {
            if (archive) {
                archive.close(true)
            }
            if (inbox) {
                inbox.close(true)
            }
            store.close()
        }
    }

    @CompileStatic
    private Folder openFolder(final Store store, final String name) {
        final Folder folder = store.getFolder(name)
        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES)
        }
        folder.open(Folder.READ_WRITE)
        return folder
    }

}
