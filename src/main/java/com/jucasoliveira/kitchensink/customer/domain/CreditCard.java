package com.jucasoliveira.kitchensink.customer.domain;

public record CreditCard(String cardNumber, String cardType, String expiryDate) {
    public static final CreditCard EMPTY = new CreditCard(null, null, null);

    public String expiryMonth() {
        int slash = this.expiryDate == null ? -1 : this.expiryDate.indexOf('/');
        return slash == -1 ? "01" : this.expiryDate.substring(0, slash);
    }

    public String expiryYear() {
        int slash = this.expiryDate == null ? -1 : this.expiryDate.indexOf('/');
        return slash == -1 ? "2010" : this.expiryDate.substring(slash + 1);
    }
}
