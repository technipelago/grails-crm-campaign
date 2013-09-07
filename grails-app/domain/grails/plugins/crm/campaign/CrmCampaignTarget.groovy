package grails.plugins.crm.campaign

/**
 * Campaign Target Group.
 */
class CrmCampaignTarget {

    public static final int DIFF = -1 // REMOVE
    public static final int ADD = 0 // ADD
    public static final int INTERSECT = 1 // KEEP

    int orderIndex
    int operation
    String name
    String description
    String uriString

    static belongsTo = [campaign: CrmCampaign]

    static constraints = {
        operation(inList: [DIFF, ADD, INTERSECT])
        name(maxSize: 80, blank: false, unique: ['campaign'])
        description(maxSize: 2000, nullable: true)
        uriString(maxSize: 2000, blank: false) // TODO should we allow longer URIs?
    }

    static transients = ["uri"]

    transient URI getUri() {
        uriString ? new URI(uriString) : null
    }

    transient void setUri(URI uri) {
        uriString = uri ? uri.toASCIIString() : null
    }

    transient void setUri(String uri) {
        uriString = uri ? new URI(uri).toASCIIString() : null
    }

    @Override
    String toString() {
        name.toString()
    }
}
