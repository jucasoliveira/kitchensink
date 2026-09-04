package com.jucasoliveira.kitchensink.customer.adapter.persistence.jpa;

import com.jucasoliveira.kitchensink.customer.domain.Account;
import com.jucasoliveira.kitchensink.customer.domain.AccountStatus;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.CreditCard;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;
import com.jucasoliveira.kitchensink.customer.domain.Profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The customer aggregate as one relational row — issue 4.6.
 *
 * <p>The legacy spent six CMP entity beans on this graph: {@code CustomerEJB} joined to
 * {@code AccountEJB}, {@code AccountEJB} to {@code ContactInfoEJB} and {@code CreditCardEJB},
 * {@code ContactInfoEJB} to {@code AddressEJB}, {@code CustomerEJB} to {@code ProfileEJB}
 * ({@code components/customer/src/ejb-jar.xml}), plus {@code UserEJB} in the signon component
 * joined on {@code userId = userName}. Seven container-generated tables and six joins to load one
 * shopper.
 *
 * <p>They are columns here, not tables, and that is the point of the pairing with
 * {@code CustomerDocument}. The legacy's own configuration says these are <em>owned</em> values
 * rather than entities — {@code <cascade-delete/>} at {@code ejb-jar.xml:280} and {@code :343} —
 * but CMP 2.0 had no way to spell an embedded value, so every value type got a table. Contrast
 * {@code CategoryEntity}, where the JPA adapter deliberately <em>rebuilds</em>
 * {@code category_details} as a real second table: those rows are one-per-locale, a genuine
 * collection, and a table is the correct relational model for them. The difference between the two
 * adapters is the finding.
 *
 * <p>No {@code @Embeddable} value classes: an embeddable needs a no-arg constructor and a
 * non-final class, so each of the four would have to be hand-written as a mutable class that
 * mirrors a domain record. There is nothing else in the application to reuse them from, so the
 * mapping lives in {@link #from(Customer)} and {@link #toDomain()} exactly as
 * {@code CustomerDocument}'s does.
 *
 * <p>This cannot be a record — JPA requires a no-arg constructor and a non-final class — which is
 * the same small tax {@code CategoryEntity} pays and the clearest illustration of what the
 * relational mapping costs that the document mapping does not.
 */
@Entity
@Table(name = "customer")
public class CustomerEntity {

    /** CustomerEJB's primary key ({@code ejb-jar.xml:59}) and UserEJB's userName, in one column. */
    @Id
    @Column(name = "userid", length = 25)
    private String userId;

    /** Issue 1.8, finding #1: a BCrypt hash where UserEJB stored the password in clear. */
    @Column(nullable = false)
    private String passwordHash;

    /** AccountEJB.status — AccountLocalHome.Active, as a string rather than an ordinal. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    // ContactInfoEJB
    private String givenName;
    private String familyName;
    private String telephone;
    private String email;

    // AddressEJB. streetName2 is the aggregate's only optional field and stays nullable, so that a
    // missing value is NULL here and an absent key in the document — the asymmetry
    // CustomerRepositoryContract exists to flatten.
    private String streetName1;
    private String streetName2;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    // CreditCardEJB. Always the empty card in this slice: AccountEJB.ejbPostCreate:87-89 created it
    // empty and the screens that filled it are T3, deferred under ADR-0006.
    private String cardNumber;
    private String cardType;
    private String expiryDate;

    // ProfileEJB, all four CMP fields.
    private String preferredLanguage;
    private String favoriteCategory;
    private boolean myListPreference;
    private boolean bannerPreference;

    protected CustomerEntity() {
    }

    static CustomerEntity from(Customer customer) {
        ContactInfo ci = customer.account().contactInfo();
        Address a = ci.address();
        CreditCard cc = customer.account().creditCard();
        Profile p = customer.profile();

        CustomerEntity entity = new CustomerEntity();
        entity.userId = customer.userId();
        entity.passwordHash = customer.passwordHash().value();
        entity.status = customer.account().status();

        entity.givenName = ci.givenName();
        entity.familyName = ci.familyName();
        entity.telephone = ci.telephone();
        entity.email = ci.email();

        entity.streetName1 = a.streetName1();
        entity.streetName2 = a.streetName2();
        entity.city = a.city();
        entity.state = a.state();
        entity.zipCode = a.zipCode();
        entity.country = a.country();

        entity.cardNumber = cc.cardNumber();
        entity.cardType = cc.cardType();
        entity.expiryDate = cc.expiryDate();

        entity.preferredLanguage = p.preferredLanguage();
        entity.favoriteCategory = p.favoriteCategory();
        entity.myListPreference = p.myListPreference();
        entity.bannerPreference = p.bannerPreference();
        return entity;
    }

    Customer toDomain() {
        Address address = new Address(this.streetName1, this.streetName2, this.city, this.state, this.zipCode,
                this.country);
        ContactInfo contactInfo = new ContactInfo(this.givenName, this.familyName, this.telephone, this.email,
                address);
        CreditCard creditCard = new CreditCard(this.cardNumber, this.cardType, this.expiryDate);
        Profile profile = new Profile(this.preferredLanguage, this.favoriteCategory, this.myListPreference,
                this.bannerPreference);

        return new Customer(this.userId, new PasswordHash(this.passwordHash),
                new Account(this.status, contactInfo, creditCard), profile);
    }

}
