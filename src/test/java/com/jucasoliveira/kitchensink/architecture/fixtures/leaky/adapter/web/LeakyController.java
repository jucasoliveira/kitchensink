package com.jucasoliveira.kitchensink.architecture.fixtures.leaky.adapter.web;

import com.jucasoliveira.kitchensink.architecture.fixtures.leaky.adapter.persistence.mongo.LeakyDocument;

/**
 * Violates {@code the_web_adapter_does_not_reach_into_persistence}: a controller that goes
 * straight at a store type instead of through an application service. ADR-0003 — both the
 * Thymeleaf screens and the REST resource sit behind the same services, so neither may be
 * store-specific.
 */
public final class LeakyController {

	public LeakyDocument show(String id) {
		return new LeakyDocument(id);
	}

}
