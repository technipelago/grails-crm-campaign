package test

class TestEntity {

    String name
    String postalCode
    String city
    Integer age

    static constraints = {
        name(maxSize: 100)
        postalCode(maxSize: 10, nullable: true)
        city(maxSize: 50, nullable: true)
        age(nullable: true)
    }

    String toString() {
        name
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        TestEntity that = (TestEntity) o

        if (id != that.id) return false

        return true
    }

    int hashCode() {
        return (id != null ? id.hashCode() : 0)
    }
}
