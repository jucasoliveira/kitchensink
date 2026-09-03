package com.jucasoliveira.kitchensink.architecture.fixtures.leaky.application;

import com.jucasoliveira.kitchensink.architecture.fixtures.leaky.adapter.persistence.mongo.LeakyDocument;

/**
 * Violates {@code the_application_layer_does_not_know_its_adapters}: an application service that
 * names a persistence adapter type. ADR-0005 §2-4 makes the adapter a profile decision at runtime;
 * this import would make it a compile-time one.
 */
public final class LeakyService {

	public LeakyDocument load(String id) {
		return new LeakyDocument(id);
	}

}
