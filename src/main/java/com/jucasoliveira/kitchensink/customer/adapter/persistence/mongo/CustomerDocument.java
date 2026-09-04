package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import com.jucasoliveira.kitchensink.customer.domain.Account;
import com.jucasoliveira.kitchensink.customer.domain.AccountStatus;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.CreditCard;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;
import com.jucasoliveira.kitchensink.customer.domain.Profile;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("customers")
record CustomerDocument(@Id String userId, String passwordHash, AccountDocument account, ProfileDocument profile) {
        record AccountDocument(AccountStatus status, ContactInfoDocument contactInfo, CreditCardDocument creditCard) {
        }

        record ContactInfoDocument(String givenName, String familyName, String telephone, String email,
                        AddressDocument address) {
        }

        record CreditCardDocument(String cardNumber, String cardType, String expiryDate) {
        }

        record AddressDocument(String streetName1, String streetName2, String city, String state, String zipCode,
                        String country) {
        }

        record ProfileDocument(String preferredLanguage, String favoriteCategory,
                        boolean myListPreference, boolean bannerPreference) {
        }

        static CustomerDocument from(Customer c) {
                ContactInfo ci = c.account().contactInfo();
                Address a = ci.address();
                Profile p = c.profile();
                CreditCard cc = c.account().creditCard();
                return new CustomerDocument(c.userId(), c.passwordHash().value(),
                                new AccountDocument(c.account().status(), new ContactInfoDocument(
                                                ci.givenName(), ci.familyName(), ci.telephone(), ci.email(),
                                                new AddressDocument(a.streetName1(), a.streetName2(), a.city(),
                                                                a.state(), a.zipCode(), a.country())),
                                                new CreditCardDocument(cc.cardNumber(), cc.cardType(),
                                                                cc.expiryDate())),
                                new ProfileDocument(p.preferredLanguage(), p.favoriteCategory(),
                                                p.myListPreference(), p.bannerPreference()));
        }

        Customer toDomain() {
                ContactInfoDocument ci = this.account.contactInfo();
                AddressDocument a = ci.address();
                ProfileDocument p = this.profile;
                CreditCardDocument cc = this.account.creditCard();
                return new Customer(this.userId, new PasswordHash(this.passwordHash),
                                new Account(this.account.status(), new ContactInfo(
                                                ci.givenName(), ci.familyName(), ci.telephone(), ci.email(),
                                                new Address(a.streetName1(), a.streetName2(), a.city(), a.state(),
                                                                a.zipCode(), a.country())),
                                                new CreditCard(cc.cardNumber(), cc.cardType(), cc.expiryDate())),
                                new Profile(p.preferredLanguage(), p.favoriteCategory(),
                                                p.myListPreference(), p.bannerPreference()));
        }
}
