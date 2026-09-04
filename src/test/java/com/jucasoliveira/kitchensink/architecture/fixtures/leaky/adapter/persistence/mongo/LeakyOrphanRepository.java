package com.jucasoliveira.kitchensink.architecture.fixtures.leaky.adapter.persistence.mongo;

/**
 * Violates {@code persistence_adapters_implement_a_port} (issue 7.1): a class that sits in a
 * persistence-adapter package and is named like an adapter, but implements no port and names
 * nothing in the application layer.
 *
 * <p>In the running application this is the shape that starts a context with the port unsatisfied:
 * component scanning finds it, {@code @Profile} activates it, and the service that wanted a
 * {@code CustomerRepository} still has none. It is the failure the profile switch is most likely
 * to produce and the least likely to be noticed, because the class itself looks right.
 */
public final class LeakyOrphanRepository {

	public String find(String id) {
		return id;
	}

}
